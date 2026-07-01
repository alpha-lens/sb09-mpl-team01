package com.codeit.mpl.domain.review.cotroller;

import com.codeit.mpl.domain.review.cotroller.api.ReviewApi;
import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewSearchRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.service.ReviewService;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewController implements ReviewApi {

  private final ReviewService reviewService;
  private final UserService userService;

  // 리뷰 생성
  @PostMapping
  public ResponseEntity<ReviewDto> createReview(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody ReviewCreateRequest request
  ) {
    UUID authorId = userService.resolveUserId(userDetails.getUsername());
    ReviewDto response = reviewService.createReview(authorId, request);
    return ResponseEntity.ok(response);
  }

  // 리뷰 수정
  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewDto> updateReview(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewUpdateRequest request
  ) {
    UUID authorId = userService.resolveUserId(userDetails.getUsername());
    ReviewDto response = reviewService.updateReview(authorId, reviewId, request);
    return ResponseEntity.ok(response);
  }

  // 리뷰 삭제
  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> deleteReview(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID reviewId
  ) {
    UUID authorId = userService.resolveUserId(userDetails.getUsername());
    reviewService.deleteReview(authorId, reviewId);
    return ResponseEntity.noContent().build();
  }

  // 리뷰 목록 조회
  @GetMapping
  public ResponseEntity<CursorPageResponseDto<ReviewDto>> getReviews(
      @Valid @ModelAttribute ReviewSearchRequest request
  ) {
    CursorPageResponseDto<ReviewDto> response = reviewService.getReviews(
        request.getContentId(),
        request.getCursor(),
        request.getIdAfter(),
        request.getLimit(),
        request.getSortBy(),
        request.getSortDirection()
    );
    return ResponseEntity.ok(response);
  }
}