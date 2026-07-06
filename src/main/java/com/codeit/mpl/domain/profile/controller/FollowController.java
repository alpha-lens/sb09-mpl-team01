package com.codeit.mpl.domain.profile.controller;

import com.codeit.mpl.domain.profile.controller.api.FollowApi;
import com.codeit.mpl.domain.profile.dto.request.FollowRequest;
import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.service.FollowService;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    FollowDto response = followService.follow(followerId, request.followeeId());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/followed-by-me")
  public ResponseEntity<String> getFollowedByMe(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestParam(value = "followeeId", required = true) UUID followeeId
  ) {
    UUID followerId = userPrincipal.userId();
    FollowDto response = followService.getFollowedByMe(followerId, followeeId);

    String json;
    try {
      json = response == null ? "null" : objectMapper.writeValueAsString(response);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(json);
  }

  @GetMapping("/count")
  public ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId) {
    long count = followService.getFollowerCount(followeeId);
    return ResponseEntity.ok(count);
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID followId
  ) {
    UUID followerId = userPrincipal.userId();
    followService.unfollow(followerId, followId);
    return ResponseEntity.noContent().build();
  }
}