package com.codeit.mpl.domain.review.service;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.review.mapper.ReviewMapper;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.exception.review.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final ReviewMapper reviewMapper;

  public ReviewDto createReview(UUID authorId, ReviewCreateRequest request) {
    log.info("리뷰 생성 요청 - authorId={}, contentId={}, rating={}", authorId, request.contentId(), request.rating());

    User author = userRepository.findById(authorId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    Content content = contentRepository.findById(request.contentId())
        .orElseThrow(() -> new MplException(ErrorCode.CONTENT_NOT_FOUND));

    if (reviewRepository.existsByAuthorAndContent(author, content)) {
      log.warn("이미 존재하는 리뷰 재작성 시도 - authorId={}, contentId={}", authorId, request.contentId());
      throw new ReviewAlreadyExistsException();
    }

    Review review = new Review(author, content, request.text(), request.rating());

    try {
      reviewRepository.save(review);
    } catch (DataIntegrityViolationException e) {
      String message = e.getMostSpecificCause().getMessage();
      if (message != null && message.contains("uk_review_author_content")) {
        log.warn("DB 제약조건 위반으로 리뷰 중복 생성 차단 - authorId={}, contentId={}", authorId, request.contentId());
        throw new ReviewAlreadyExistsException();
      }
      log.error("리뷰 저장 중 예기치 못한 DB 오류 - authorId={}, contentId={}", authorId, request.contentId(), e);
      throw e;
    }

    updateContentReviewStats(content);

    log.info("리뷰 생성 완료 - reviewId={}, authorId={}, contentId={}", review.getId(), authorId, request.contentId());
    return reviewMapper.toDto(review);
  }


  public ReviewDto updateReview(UUID authorId, UUID reviewId, ReviewUpdateRequest request) {
    log.info("리뷰 수정 요청 - reviewId={}, authorId={}", reviewId, authorId);

    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> {
          log.warn("존재하지 않는 리뷰 수정 시도 - reviewId={}", reviewId);
          return new ReviewNotFoundException();
        });

    if (!review.getAuthor().getId().equals(authorId)) {
      log.warn("리뷰 수정 권한 없음 - reviewId={}, requesterId={}, authorId={}",
          reviewId, authorId, review.getAuthor().getId());
      throw new ReviewForbiddenException();
    }

    review.update(request.text(), request.rating());
    updateContentReviewStats(review.getContent());
    log.info("리뷰 수정 완료 - reviewId={}", reviewId);
    return reviewMapper.toDto(review);
  }


  public void deleteReview(UUID authorId, UUID reviewId) {
    log.info("리뷰 삭제 요청 - reviewId={}, authorId={}", reviewId, authorId);

    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> {
          log.warn("존재하지 않는 리뷰 삭제 시도 - reviewId={}", reviewId);
          return new ReviewNotFoundException();
        });

    if (!review.getAuthor().getId().equals(authorId)) {
      log.warn("리뷰 삭제 권한 없음 - reviewId={}, requesterId={}, authorId={}",
          reviewId, authorId, review.getAuthor().getId());
      throw new ReviewForbiddenException();
    }

    Content content = review.getContent();
    reviewRepository.delete(review);
    updateContentReviewStats(content);
    log.info("리뷰 삭제 완료 - reviewId={}, authorId={}", reviewId, authorId);
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
    log.debug("리뷰 목록 조회 요청 - contentId={}, limit={}, sortBy={}, sortDirection={}",
        contentId, limit, sortBy, sortDirection);

    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new MplException(ErrorCode.CONTENT_NOT_FOUND));

    validateCursorPair(cursor, idAfter);
    validateSortBy(sortBy);

    Sort.Direction direction = sortDirection == Direction.ASCENDING
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    Pageable pageable = PageRequest.of(
        0,
        limit + 1,
        Sort.by(direction, sortBy).and(Sort.by(direction, "id"))
    );

    Specification<Review> contentSpec = (root, query, cb) -> cb.equal(root.get("content"), content);
    Specification<Review> cursorSpec = createCursorSpecification(cursor, idAfter, sortBy, sortDirection);
    Specification<Review> specification = contentSpec.and(cursorSpec);

    Page<Review> reviewPage = reviewRepository.findAll(specification, pageable);

    List<Review> reviews = reviewPage.getContent();
    boolean hasNext = reviews.size() > limit;

    List<Review> pageReviews = hasNext ? reviews.subList(0, limit) : reviews;

    List<ReviewDto> reviewDtos = pageReviews.stream()
        .map(reviewMapper::toDto)
        .toList();

    String nextCursor = null;
    String nextIdAfter = null;

    if (hasNext && !pageReviews.isEmpty()) {
      Review last = pageReviews.get(pageReviews.size() - 1);
      nextCursor = getCursorValue(last, sortBy);
      nextIdAfter = last.getId().toString();
    }

    long totalCount = reviewRepository.count(contentSpec);

    log.debug("리뷰 목록 조회 완료 - contentId={}, 결과 {}건, totalCount={}", contentId, reviewDtos.size(), totalCount);

    return new CursorPageResponseDto<>(
        reviewDtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }

  private void validateCursorPair(String cursor, String idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null && !idAfter.isBlank();

    if (hasCursor != hasIdAfter) {
      throw new InvalidReviewCursorException();
    }
  }

  private void validateSortBy(String sortBy) {
    if (!"createdAt".equals(sortBy) && !"rating".equals(sortBy)) {
      throw new InvalidReviewSortException();
    }
  }

  private Specification<Review> createCursorSpecification(
      String cursor,
      String idAfter,
      String sortBy,
      Direction sortDirection
  ) {
    return (root, query, cb) -> {
      if (cursor == null || cursor.isBlank() || idAfter == null || idAfter.isBlank()) {
        return cb.conjunction();
      }

      UUID idAfterValue = parseIdAfter(idAfter);

      if ("createdAt".equals(sortBy)) {
        Instant cursorValue = parseInstantCursor(cursor);

        Predicate sortPredicate;
        Predicate sameSortValuePredicate;

        if (sortDirection == Direction.ASCENDING) {
          sortPredicate = cb.greaterThan(root.get("createdAt"), cursorValue);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get("createdAt"), cursorValue),
              cb.greaterThan(root.get("id"), idAfterValue)
          );
        } else {
          sortPredicate = cb.lessThan(root.get("createdAt"), cursorValue);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get("createdAt"), cursorValue),
              cb.lessThan(root.get("id"), idAfterValue)
          );
        }

        return cb.or(sortPredicate, sameSortValuePredicate);
      }

      if ("rating".equals(sortBy)) {
        Integer cursorValue = parseIntCursor(cursor);

        Predicate sortPredicate;
        Predicate sameSortValuePredicate;

        if (sortDirection == Direction.ASCENDING) {
          sortPredicate = cb.greaterThan(root.get("rating"), cursorValue);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get("rating"), cursorValue),
              cb.greaterThan(root.get("id"), idAfterValue)
          );
        } else {
          sortPredicate = cb.lessThan(root.get("rating"), cursorValue);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get("rating"), cursorValue),
              cb.lessThan(root.get("id"), idAfterValue)
          );
        }

        return cb.or(sortPredicate, sameSortValuePredicate);
      }

      return cb.conjunction();
    };
  }

  private UUID parseIdAfter(String idAfter) {
    try {
      return UUID.fromString(idAfter);
    } catch (IllegalArgumentException e) {
      throw new InvalidReviewCursorException();
    }
  }

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new InvalidReviewCursorException();
    }
  }

  private Integer parseIntCursor(String cursor) {
    try {
      return Integer.parseInt(cursor);
    } catch (NumberFormatException e) {
      throw new InvalidReviewCursorException();
    }
  }

  private String getCursorValue(Review review, String sortBy) {
    if ("createdAt".equals(sortBy)) {
      return review.getCreatedAt().toString();
    }
    if ("rating".equals(sortBy)) {
      return String.valueOf(review.getRating());
    }
    throw new InvalidReviewSortException();
  }

  private void updateContentReviewStats(Content content) {
    if (content == null || content.getId() == null) {
      return;
    }
    List<com.codeit.mpl.domain.review.dto.response.ReviewStats> statsList = reviewRepository.findReviewStatsByContentIds(List.of(content.getId()));
    if (statsList != null && !statsList.isEmpty() && statsList.get(0) != null) {
      com.codeit.mpl.domain.review.dto.response.ReviewStats stats = statsList.get(0);
      content.updateReviewStats(stats.averageRating(), stats.reviewCount() != null ? Math.toIntExact(stats.reviewCount()) : 0);
    } else {
      content.updateReviewStats(0.0, 0);
    }
    contentRepository.save(content);
  }
}



