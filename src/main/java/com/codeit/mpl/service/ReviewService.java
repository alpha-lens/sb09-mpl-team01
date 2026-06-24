package com.codeit.mpl.service;

import com.codeit.mpl.dto.request.ReviewCreateRequest;
import com.codeit.mpl.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.dto.response.CursorPageResponseDto;
import com.codeit.mpl.dto.response.ReviewDto;
import com.codeit.mpl.dto.response.Direction;
import java.util.List;
import com.codeit.mpl.entity.ReviewEntity;
import com.codeit.mpl.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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

    ReviewEntity review = new ReviewEntity(
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
    ReviewEntity review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 리뷰입니다."));

    if (!review.getAuthorId().equals(authorId)) {
      throw new IllegalArgumentException("리뷰 작성자만 수정할 수 있습니다.");
    }

    review.update(request.text(), request.rating());
    return ReviewDto.from(review);
  }

  public void deleteReview(UUID authorId, UUID reviewId) {
    ReviewEntity review = reviewRepository.findById(reviewId)
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
    List<ReviewEntity> reviews = reviewRepository.findByContentId(contentId);

    List<ReviewDto> reviewDtos = reviews.stream()
        .map(ReviewDto::from)
        .toList();

    return new CursorPageResponseDto<>(
        reviewDtos,
        null,
        null,
        false,
        reviewDtos.size(),
        sortBy,
        sortDirection
    );
  }
}