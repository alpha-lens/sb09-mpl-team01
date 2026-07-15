package com.codeit.mpl.domain.review.cotroller;

import com.codeit.mpl.domain.review.cotroller.api.ReviewApi;
import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewSearchRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.service.ReviewService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewController implements ReviewApi {

  private final ReviewService reviewService;

  @PostMapping
  public ResponseEntity<ReviewDto> createReview(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody ReviewCreateRequest request
  ) {
    UUID authorId = userPrincipal.userId();
    log.info("[Review API] POST /api/reviews 요청 - authorId={}, contentId={}", authorId, request.contentId());
    ReviewDto response = reviewService.createReview(authorId, request);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewDto> updateReview(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewUpdateRequest request
  ) {
    UUID authorId = userPrincipal.userId();
    log.info("[Review API] PATCH /api/reviews/{} 요청 - authorId={}", reviewId, authorId);
    ReviewDto response = reviewService.updateReview(authorId, reviewId, request);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> deleteReview(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID reviewId
  ) {
    UUID authorId = userPrincipal.userId();
    log.info("[Review API] DELETE /api/reviews/{} 요청 - authorId={}", reviewId, authorId);
    reviewService.deleteReview(authorId, reviewId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  public ResponseEntity<CursorPageResponseDto<ReviewDto>> getReviews(
      @Valid @ModelAttribute ReviewSearchRequest request
  ) {
    log.debug("[Review API] GET /api/reviews 요청 - contentId={}", request.getContentId());
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