package com.codeit.mpl.domain.profile.controller;

import com.codeit.mpl.domain.profile.controller.api.FollowApi;
import com.codeit.mpl.domain.profile.dto.request.FollowRequest;
import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.service.FollowService;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController implements FollowApi {

  private final FollowService followService;
  private final ObjectMapper objectMapper;

  @PostMapping
  public ResponseEntity<FollowDto> follow(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody FollowRequest request
  ) {
    UUID followerId = userPrincipal.userId();
    log.info("[Follow API] POST /api/follows 요청 - followerId={}, followeeId={}", followerId, request.followeeId());
    FollowDto response = followService.follow(followerId, request.followeeId());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/followed-by-me")
  public ResponseEntity<String> getFollowedByMe(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestParam(value = "followeeId", required = true) UUID followeeId
  ) {
    UUID followerId = userPrincipal.userId();
    log.debug("[Follow API] GET /api/follows/followed-by-me 요청 - followerId={}, followeeId={}", followerId, followeeId);
    FollowDto response = followService.getFollowedByMe(followerId, followeeId);

    String json;
    try {
      json = response == null ? "null" : objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      log.error("[Follow API] followed-by-me 응답 직렬화 실패 - followerId={}, followeeId={}", followerId, followeeId, e);
      throw new RuntimeException(e);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(json);
  }

  @GetMapping("/count")
  public ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId) {
    log.debug("[Follow API] GET /api/follows/count 요청 - followeeId={}", followeeId);
    long count = followService.getFollowerCount(followeeId);
    return ResponseEntity.ok(count);
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID followId
  ) {
    UUID followerId = userPrincipal.userId();
    log.info("[Follow API] DELETE /api/follows/{} 요청 - followerId={}", followId, followerId);
    followService.unfollow(followerId, followId);
    return ResponseEntity.noContent().build();
  }
}