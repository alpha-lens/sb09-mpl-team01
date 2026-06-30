package com.codeit.mpl.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.review.mapper.ReviewMapper;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.review.service.ReviewService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.MplException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4.5);

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
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4.5);

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
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4.5);

    when(userRepository.findById(authorId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> reviewService.createReview(authorId, request))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("리뷰 수정 성공")
  void updateReview_success() {
    UUID authorId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", 3.5);

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
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", 3.5);

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
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", 3.5);

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
        new ReviewDto(UUID.randomUUID(), contentId, UUID.randomUUID(), "좋아요", 4.0, null, null)
    );

    CursorPageResponseDto<ReviewDto> response = reviewService.getReviews(
        contentId, null, null, 10, "createdAt", Direction.DESCENDING
    );

    assertThat(response.data()).hasSize(1);
    assertThat(response.hasNext()).isFalse();
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
}