package com.codeit.mpl.domain.review.service;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.repository.ContentRepository;


import com.codeit.mpl.domain.review.dto.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.ReviewDto;
import com.codeit.mpl.domain.review.dto.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.review.mapper.ReviewMapper;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final ReviewMapper reviewMapper;

  public ReviewDto createReview(UUID authorId, ReviewCreateRequest request) {
    User author = userRepository.findById(authorId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

    Content content = contentRepository.findById(request.contentId())
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    if (reviewRepository.existsByAuthorAndContent(author, content)) {
      throw new IllegalArgumentException("이미 리뷰를 작성했습니다.");
    }

    Review review = new Review(author, content, request.text(), request.rating());

    try {
      reviewRepository.save(review);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException("이미 리뷰를 작성했습니다.");
    }

    return reviewMapper.toDto(review);
  }

  public ReviewDto updateReview(UUID authorId, UUID reviewId, ReviewUpdateRequest request) {
    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리뷰입니다."));

    if (!review.getAuthor().getId().equals(authorId)) {
      throw new IllegalArgumentException("리뷰 작성자만 수정할 수 있습니다.");
    }

    review.update(request.text(), request.rating());
    return reviewMapper.toDto(review);
  }

  public void deleteReview(UUID authorId, UUID reviewId) {
    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리뷰입니다."));

    if (!review.getAuthor().getId().equals(authorId)) {
      throw new IllegalArgumentException("리뷰 작성자만 삭제할 수 있습니다.");
    }

    reviewRepository.delete(review);
  }

  @Transactional(readOnly = true)
  public CursorPageResponseDto<ReviewDto> getReviews(
      UUID contentId,
      String cursor,
      String idAfter,
      int limit,
      String sortBy,
      Direction sortDirection
  ) {
    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    Sort.Direction direction = sortDirection == Direction.ASCENDING
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    Pageable pageable = PageRequest.of(0, limit, Sort.by(direction, sortBy));

    Page<Review> reviewPage = reviewRepository.findByContent(content, pageable);

    List<ReviewDto> reviewDtos = reviewPage.getContent().stream()
        .map(reviewMapper::toDto)
        .toList();

    return new CursorPageResponseDto<>(
        reviewDtos,
        null,
        null,
        reviewPage.hasNext(),
        reviewPage.getTotalElements(),
        sortBy,
        sortDirection
    );
  }
}