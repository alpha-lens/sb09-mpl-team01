package com.codeit.mpl.controller;

import com.codeit.mpl.dto.request.ReviewCreateRequest;
import com.codeit.mpl.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.dto.response.ReviewDto;
import com.codeit.mpl.service.ReviewService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewController {

  private final ReviewService reviewService;

  // 임시 authorId (지금은 임시 작성자ID 사용. 나중에 교체예정)
  private static final UUID TEMP_AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  // 리뷰 생성
  @PostMapping
  public ResponseEntity<ReviewDto> createReview(@RequestBody ReviewCreateRequest request) {
    ReviewDto response = reviewService.createReview(TEMP_AUTHOR_ID, request);
    return ResponseEntity.ok(response);
  }

  // 리뷰 수정
  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewDto> updateReview(
      @PathVariable UUID reviewId,
      @RequestBody ReviewUpdateRequest request
  ) {
    ReviewDto response = reviewService.updateReview(TEMP_AUTHOR_ID, reviewId, request);
    return ResponseEntity.ok(response);
  }

  // 리뷰 삭제
  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> deleteReview(@PathVariable UUID reviewId) {
    reviewService.deleteReview(TEMP_AUTHOR_ID, reviewId);
    return ResponseEntity.noContent().build();
  }
}