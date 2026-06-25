package com.codeit.mpl.domain.review.service;

import com.codeit.mpl.domain.review.dto.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.ReviewDto;
import com.codeit.mpl.domain.review.dto.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
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

  public ReviewDto createReview(UUID authorId, ReviewCreateRequest request) {
    if (reviewRepository.existsByAuthorIdAndContentId(authorId, request.contentId())) {
      throw new IllegalArgumentException("이미 리뷰를 작성했습니다.");
    }

    Review review = new Review(
        authorId,
        request.contentId(),
        request.text(),
        request.rating()
    );

    try {
      reviewRepository.save(review);
    } catch (DataIntegrityViolationException e) {
      throw new IllegalArgumentException("이미 리뷰를 작성했습니다.");
    }

    return ReviewDto.from(review);
  }

  public ReviewDto updateReview(UUID authorId, UUID reviewId, ReviewUpdateRequest request) {
    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리뷰입니다."));

    if (!review.getAuthorId().equals(authorId)) {
      throw new IllegalArgumentException("리뷰 작성자만 수정할 수 있습니다.");
    }

    review.update(request.text(), request.rating());
    return ReviewDto.from(review);
  }

  public void deleteReview(UUID authorId, UUID reviewId) {
    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리뷰입니다."));

    if (!review.getAuthorId().equals(authorId)) {
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
    Sort.Direction direction = sortDirection == Direction.ASCENDING
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    Pageable pageable = PageRequest.of(0, limit, Sort.by(direction, sortBy));

    Page<Review> reviewPage = reviewRepository.findByContentId(contentId, pageable);

    List<ReviewDto> reviewDtos = reviewPage.getContent().stream()
        .map(ReviewDto::from)
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