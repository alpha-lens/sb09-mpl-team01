package com.codeit.mpl.domain.profile.controller.api;

import com.codeit.mpl.domain.profile.dto.request.FollowRequest;
import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.infra.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "팔로우 관리", description = "팔로우/언팔로우 API")
public interface FollowApi {

  @Operation(summary = "팔로우")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "팔로우 성공"),
      @ApiResponse(responseCode = "400", description = "자기 자신 팔로우 불가"),
      @ApiResponse(responseCode = "404", description = "사용자 없음"),
      @ApiResponse(responseCode = "409", description = "이미 팔로우한 사용자")
  })
  ResponseEntity<FollowDto> follow(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody FollowRequest request
  );

  @Operation(summary = "팔로우 여부 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공 (팔로우 중이면 정보 반환, 아니면 null 반환)")
  })
  ResponseEntity<String> getFollowedByMe(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestParam(value = "followeeId", required = true) UUID followeeId
  );

  @Operation(summary = "팔로워 수 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId);

  @Operation(summary = "팔로우 취소")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "팔로우 취소 성공"),
      @ApiResponse(responseCode = "404", description = "팔로우 관계 없음")
  })
  ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID followId
  );
}