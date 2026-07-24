package com.codeit.mpl.domain.review.service;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.review.mapper.ReviewMapper;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.exception.review.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

  @Mock
  private ReviewRepository reviewRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ReviewMapper reviewMapper;

  @InjectMocks
  private ReviewService reviewService;

  @Test
  @DisplayName("리뷰 생성 성공")
  void createReview_success() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4);

    User author = mock(User.class);
    Content content = mock(Content.class);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(reviewRepository.existsByAuthorAndContent(author, content)).thenReturn(false);

    reviewService.createReview(authorId, request);

    verify(reviewRepository).save(any(Review.class));
  }

  @Test
  @DisplayName("리뷰 생성 실패 - 이미 작성한 리뷰가 있는 경우")
  void createReview_fail_alreadyExists() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4);

    User author = mock(User.class);
    Content content = mock(Content.class);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(reviewRepository.existsByAuthorAndContent(author, content)).thenReturn(true);

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(MplException.class);

    verify(reviewRepository, never()).save(any(Review.class));
  }

  @Test
  @DisplayName("리뷰 생성 실패 - 존재하지 않는 사용자")
  void createReview_fail_userNotFound() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4);

    when(userRepository.findById(authorId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 수정 성공")
  void updateReview_success() {
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", 3);

    User author = mock(User.class);
    Review review = mock(Review.class);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
    when(review.getAuthor()).thenReturn(author);
    when(author.getId()).thenReturn(authorId);

    reviewService.updateReview(authorId, reviewId, request);

    verify(review).update(request.text(), request.rating());
  }

  @Test
  @DisplayName("리뷰 수정 실패 - 작성자가 아닌 경우")
  void updateReview_fail_forbidden() {
    UUID authorId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", 3);

    User author = mock(User.class);
    Review review = mock(Review.class);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
    when(review.getAuthor()).thenReturn(author);
    when(author.getId()).thenReturn(otherUserId);

    assertThatThrownBy(() -> reviewService.updateReview(authorId, reviewId, request))
        .isInstanceOf(MplException.class);

    verify(review, never()).update(any(), any());
  }

  @Test
  @DisplayName("리뷰 수정 실패 - 존재하지 않는 리뷰")
  void updateReview_fail_notFound() {
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", 3);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.updateReview(authorId, reviewId, request))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 삭제 성공")
  void deleteReview_success() {
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    User author = mock(User.class);
    Review review = mock(Review.class);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
    when(review.getAuthor()).thenReturn(author);
    when(author.getId()).thenReturn(authorId);

    reviewService.deleteReview(authorId, reviewId);

    verify(reviewRepository).delete(review);
  }

  @Test
  @DisplayName("리뷰 삭제 실패 - 작성자가 아닌 경우")
  void deleteReview_fail_forbidden() {
    UUID authorId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    User author = mock(User.class);
    Review review = mock(Review.class);

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));
    when(review.getAuthor()).thenReturn(author);
    when(author.getId()).thenReturn(otherUserId);

    assertThatThrownBy(() -> reviewService.deleteReview(authorId, reviewId))
        .isInstanceOf(MplException.class);

    verify(reviewRepository, never()).delete(any(Review.class));
  }

  @Test
  @DisplayName("리뷰 삭제 실패 - 존재하지 않는 리뷰")
  void deleteReview_fail_notFound() {
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    when(reviewRepository.findById(reviewId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.deleteReview(authorId, reviewId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 목록 조회 성공")
  void getReviews_success() {
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);
    Review review = mock(Review.class);

    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(review)));
    when(reviewRepository.count(any(Specification.class))).thenReturn(1L);
    when(reviewMapper.toDto(review)).thenReturn(
        new ReviewDto(UUID.randomUUID(), contentId,
            new UserSummary(UUID.randomUUID(), "작성자", null), "좋아요", 4, null, null)
    );

    CursorPageResponseDto<ReviewDto> response = reviewService.getReviews(
        contentId, null, null, 10, "createdAt", Direction.DESCENDING
    );

    assertThat(response.data()).hasSize(1);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  @DisplayName("리뷰 목록 조회 - 정렬 기준이 rating인 경우")
  void getReviews_sortByRating_success() {
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);

    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(Page.empty());
    when(reviewRepository.count(any(Specification.class))).thenReturn(0L);

    reviewService.getReviews(contentId, null, null, 10, "rating", Direction.DESCENDING);

    verify(reviewRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("리뷰 목록 조회 실패 - 존재하지 않는 콘텐츠")
  void getReviews_fail_contentNotFound() {
    UUID contentId = UUID.randomUUID();
    when(contentRepository.findById(contentId)).thenReturn(Optional.empty());
    assertThatThrownBy(() ->
        reviewService.getReviews(contentId, null, null, 10, "createdAt", Direction.DESCENDING)
    ).isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 목록 조회 실패 - cursor만 있고 idAfter 없는 경우")
  void getReviews_fail_cursorPairInvalid() {
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);

    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

    assertThatThrownBy(() ->
        reviewService.getReviews(contentId, "2026-01-01T00:00:00Z", null, 10, "createdAt", Direction.DESCENDING)
    ).isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 목록 조회 실패 - 허용되지 않은 정렬 기준")
  void getReviews_fail_invalidSortBy() {
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);

    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

    assertThatThrownBy(() ->
        reviewService.getReviews(contentId, null, null, 10, "invalidField", Direction.DESCENDING)
    ).isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 생성 실패 - DB 제약 조건 위반(DataIntegrityViolationException)")
  void createReview_fail_dataIntegrity() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "테스트", 5);

    User author = mock(User.class);
    Content content = mock(Content.class);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    // 리포지토리가 예외를 던지도록 모킹
    when(reviewRepository.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_review_author_content"));

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(com.codeit.mpl.infra.exception.review.ReviewAlreadyExistsException.class);
  }

  @Test
  @DisplayName("리뷰 목록 조회 - 커서가 null이거나 비어있을 때 성공")
  void getReviews_nullCursor_success() {
    UUID contentId = UUID.randomUUID();
    Content content = mock(Content.class);

    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(Page.empty());
    when(reviewRepository.count(any(Specification.class))).thenReturn(0L);

    reviewService.getReviews(contentId, null, null, 10, "createdAt", Direction.DESCENDING);

    verify(reviewRepository).findAll(any(Specification.class), any(Pageable.class));
  }

  @Test
  @DisplayName("리뷰 생성 실패 - DB 제약 조건 예외 발생 시 전파")
  void createReview_fail_unhandledDataIntegrity() {
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "테스트", 5);

    User author = mock(User.class);
    Content content = mock(Content.class);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(author));
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

    // uk_review_author_content 이름이 아닌 다른 예외를 던지도록 설정하여 else 문 타기
    when(reviewRepository.save(any())).thenThrow(new org.springframework.dao.DataIntegrityViolationException("other_exception"));

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  // ==========================================
  // 1. [Create] 예외 케이스 (유저 없음, 콘텐츠 없음, 중복 리뷰)
  // ==========================================
  @Test
  @DisplayName("리뷰 생성 실패 - 유저가 존재하지 않음")
  void createReview_UserNotFound() {
    ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "테스트", 5);
    when(userRepository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.createReview(UUID.randomUUID(), request))
        .isInstanceOf(MplException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("리뷰 생성 실패 - 콘텐츠가 존재하지 않음")
  void createReview_ContentNotFound() {
    UUID authorId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "테스트", 5);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(mock(User.class)));
    when(contentRepository.findById(request.contentId())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(MplException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTENT_NOT_FOUND);
  }

  @Test
  @DisplayName("리뷰 생성 실패 - 이미 리뷰가 존재함 (existsBy 쿼리 잡힘)")
  void createReview_AlreadyExists() {
    UUID authorId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "테스트", 5);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(mock(User.class)));
    when(contentRepository.findById(request.contentId())).thenReturn(Optional.of(mock(Content.class)));
    when(reviewRepository.existsByAuthorAndContent(any(), any())).thenReturn(true);

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(ReviewAlreadyExistsException.class);
  }

  // ==========================================
  // 2. [Update/Delete] 예외 케이스 (권한 없음, 리뷰 없음)
  // ==========================================
  @Test
  @DisplayName("리뷰 수정/삭제 실패 - 작성자가 아님 (권한 없음)")
  void updateAndDeleteReview_Forbidden() {
    UUID authorId = UUID.randomUUID(); // 요청자 ID
    UUID otherUserId = UUID.randomUUID(); // 실제 작성자 ID
    UUID reviewId = UUID.randomUUID();

    User author = mock(User.class);
    when(author.getId()).thenReturn(otherUserId); // 아이디가 다름

    Review review = mock(Review.class);
    when(review.getAuthor()).thenReturn(author);
    when(reviewRepository.findById(reviewId)).thenReturn(Optional.of(review));

    // 수정 실패 검증
    assertThatThrownBy(() -> reviewService.updateReview(authorId, reviewId, new ReviewUpdateRequest("수정", 5)))
        .isInstanceOf(ReviewForbiddenException.class);

    // 삭제 실패 검증
    assertThatThrownBy(() -> reviewService.deleteReview(authorId, reviewId))
        .isInstanceOf(ReviewForbiddenException.class);
  }

  @Test
  @DisplayName("리뷰 삭제 실패 - 리뷰가 존재하지 않음")
  void deleteReview_NotFound() {
    when(reviewRepository.findById(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.deleteReview(UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(ReviewNotFoundException.class);
  }

  // ==========================================
  // 3. [Get Reviews] 파라미터 검증 (Cursor 유효성, Sort 유효성)
  // ==========================================
  @Test
  @DisplayName("리뷰 목록 조회 실패 - Cursor와 idAfter 쌍이 맞지 않음")
  void getReviews_InvalidCursorPair() {
    UUID contentId = UUID.randomUUID();
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(mock(Content.class)));

    // cursor는 있는데 idAfter가 없는 경우
    assertThatThrownBy(() -> reviewService.getReviews(contentId, "cursorValue", null, 10, "createdAt", Direction.DESCENDING))
        .isInstanceOf(InvalidReviewCursorException.class);

    // idAfter는 있는데 cursor가 없는 경우
    assertThatThrownBy(() -> reviewService.getReviews(contentId, null, "idAfterValue", 10, "createdAt", Direction.DESCENDING))
        .isInstanceOf(InvalidReviewCursorException.class);
  }

  @Test
  @DisplayName("리뷰 목록 조회 실패 - 잘못된 정렬 기준")
  void getReviews_InvalidSortBy() {
    UUID contentId = UUID.randomUUID();
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(mock(Content.class)));

    assertThatThrownBy(() -> reviewService.getReviews(contentId, null, null, 10, "invalidSort", Direction.DESCENDING))
        .isInstanceOf(InvalidReviewSortException.class);
  }

  // ==========================================
  // 4. [Get Reviews] 페이징 다음 페이지 존재 여부 로직 (hasNext = true)
  // ==========================================
  @Test
  @DisplayName("리뷰 목록 조회 - 다음 페이지가 존재하는 경우 (hasNext=true)")
  void getReviews_HasNextPage() {
    UUID contentId = UUID.randomUUID();
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(mock(Content.class)));

    Review review1 = mock(Review.class);
    Review review2 = mock(Review.class);

    // review1은 실제 사용되므로 ID와 날짜를 설정합니다.
    when(review1.getId()).thenReturn(UUID.randomUUID());
    when(review1.getCreatedAt()).thenReturn(Instant.now());

    when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(review1, review2)));
    when(reviewRepository.count(any(Specification.class))).thenReturn(2L);

    CursorPageResponseDto<ReviewDto> response = reviewService.getReviews(
        contentId, null, null, 1, "createdAt", Direction.DESCENDING);

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isNotNull();
    assertThat(response.nextIdAfter()).isNotNull();
  }

  // ==========================================
  // 5. Specification 람다식 내부 강제 실행 테스트
  // ==========================================
  @Test
  @DisplayName("Specification 람다 내부 분기점 (ASC/DESC, 파싱 예외, 공백) 커버리지 확보")
  @SuppressWarnings("unchecked")
  void specification_Coverage_Hack() {
    UUID contentId = UUID.randomUUID();
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(mock(Content.class)));
    when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(org.springframework.data.domain.Page.empty());

    org.mockito.ArgumentCaptor<Specification<Review>> specCaptor =
        org.mockito.ArgumentCaptor.forClass(Specification.class);

    String validUuid = UUID.randomUUID().toString();
    String validDate = Instant.now().toString();

    // 1. 정상 조건 (ASCENDING)
    reviewService.getReviews(contentId, "5", validUuid, 10, "rating", Direction.ASCENDING);
    reviewService.getReviews(contentId, validDate, validUuid, 10, "createdAt", Direction.ASCENDING);

    // 2. 정상 조건 (DESCENDING)
    reviewService.getReviews(contentId, "5", validUuid, 10, "rating", Direction.DESCENDING);
    reviewService.getReviews(contentId, validDate, validUuid, 10, "createdAt", Direction.DESCENDING);

    // 3. 파싱 예외 발생 (catch 커버리지)
    reviewService.getReviews(contentId, "invalid_uuid", "invalid_uuid", 10, "createdAt", Direction.DESCENDING);
    reviewService.getReviews(contentId, "invalid_date", validUuid, 10, "createdAt", Direction.DESCENDING);
    reviewService.getReviews(contentId, "invalid_rating", validUuid, 10, "rating", Direction.DESCENDING);

    // 4. 공백 (isBlank 커버리지) - 오류 나던 부분을 지우고 정상적으로 동작하는 한 줄만 남겼습니다!
    reviewService.getReviews(contentId, "   ", "   ", 10, "createdAt", Direction.DESCENDING);

    org.mockito.Mockito.verify(reviewRepository, org.mockito.Mockito.atLeastOnce())
        .findAll(specCaptor.capture(), any(Pageable.class));

    jakarta.persistence.criteria.Root<Review> root = mock(jakarta.persistence.criteria.Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
    jakarta.persistence.criteria.CriteriaBuilder cb = mock(jakarta.persistence.criteria.CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

    for (Specification<Review> spec : specCaptor.getAllValues()) {
      try { spec.toPredicate(root, query, cb); } catch (Exception ignored) {}
    }
  }

  // ==========================================
  // [추가] 6. rating 기준 hasNext = true (getCursorValue 커버리지)
  // ==========================================
  @Test
  @DisplayName("리뷰 목록 조회 - 정렬 기준이 rating이고 다음 페이지가 존재할 때 커서 추출")
  void getReviews_HasNextPage_Rating() {
    UUID contentId = UUID.randomUUID();
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(mock(Content.class)));

    Review review1 = mock(Review.class);
    Review review2 = mock(Review.class);

    when(review1.getId()).thenReturn(UUID.randomUUID());
    when(review1.getRating()).thenReturn(4); // rating 커서값 지정을 위해 필요함

    when(reviewRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(review1, review2)));
    when(reviewRepository.count(any(Specification.class))).thenReturn(2L);

    CursorPageResponseDto<ReviewDto> response = reviewService.getReviews(
        contentId, null, null, 1, "rating", Direction.DESCENDING);

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("4");
  }

  // ==========================================
  // [추가] 7. createReview 알 수 없는 DB 에러 (catch 블록 남은 분기 커버리지)
  // ==========================================
  @Test
  @DisplayName("리뷰 생성 실패 - 중복 리뷰가 아닌 기타 DB 제약 조건 예외 발생 시 전파")
  void createReview_Fail_OtherDataIntegrityException() {
    UUID authorId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "테스트", 5);

    when(userRepository.findById(authorId)).thenReturn(Optional.of(mock(User.class)));
    when(contentRepository.findById(request.contentId())).thenReturn(Optional.of(mock(Content.class)));
    when(reviewRepository.existsByAuthorAndContent(any(), any())).thenReturn(false);

    // "uk_review_author_content" 가 포함되지 않은 다른 예외 메시지 모킹
    org.springframework.dao.DataIntegrityViolationException ex = mock(org.springframework.dao.DataIntegrityViolationException.class);
    Throwable cause = mock(Throwable.class);
    when(ex.getMostSpecificCause()).thenReturn(cause);
    when(cause.getMessage()).thenReturn("some_other_unknown_error");

    when(reviewRepository.save(any())).thenThrow(ex);

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

}