package com.codeit.mpl.domain.review.cotroller.api;

import com.codeit.mpl.domain.review.dto.request.ReviewCreateRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewSearchRequest;
import com.codeit.mpl.domain.review.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "리뷰 관리", description = "리뷰 CRUD API")
public interface ReviewApi {

  @Operation(summary = "리뷰 목록 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  ResponseEntity<CursorPageResponseDto<ReviewDto>> getReviews(
      @ModelAttribute ReviewSearchRequest request
  );

  @Operation(summary = "리뷰 생성")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "생성 성공"),
      @ApiResponse(responseCode = "400", description = "입력값 검증 실패")
  })
  ResponseEntity<ReviewDto> createReview(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody ReviewCreateRequest request
  );

  @Operation(summary = "리뷰 수정")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "수정 성공"),
      @ApiResponse(responseCode = "403", description = "권한 없음"),
      @ApiResponse(responseCode = "404", description = "리뷰 없음")
  })
  ResponseEntity<ReviewDto> updateReview(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewUpdateRequest request
  );

  @Operation(summary = "리뷰 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공"),
      @ApiResponse(responseCode = "403", description = "권한 없음"),
      @ApiResponse(responseCode = "404", description = "리뷰 없음")
  })
  ResponseEntity<Void> deleteReview(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID reviewId
  );
}