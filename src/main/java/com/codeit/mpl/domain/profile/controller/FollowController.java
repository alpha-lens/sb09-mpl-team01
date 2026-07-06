package com.codeit.mpl.domain.profile.controller;

import com.codeit.mpl.domain.profile.controller.api.FollowApi;
import com.codeit.mpl.domain.profile.dto.request.FollowRequest;
import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.service.FollowService;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.exception.follow.FollowForbiddenException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
  private final UserService userService;

  @PostMapping
  public ResponseEntity<FollowDto> follow(
      @AuthenticationPrincipal UserDetails userDetails,
      @Valid @RequestBody FollowRequest request
  ) {
    UUID followerId = resolveAuthenticatedUserId(userDetails);
    FollowDto response = followService.follow(followerId, request.followeeId());
    return ResponseEntity.ok(response);
  }

  @GetMapping("/followed-by-me")
  public ResponseEntity<FollowDto> getFollowedByMe(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(value = "followeeId", required = true) UUID followeeId
  ) {
    UUID followerId = resolveAuthenticatedUserId(userDetails);
    FollowDto response = followService.getFollowedByMe(followerId, followeeId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/count")
  public ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId) {
    long count = followService.getFollowerCount(followeeId);
    return ResponseEntity.ok(count);
  }

  @DeleteMapping("/{followId}")
  public ResponseEntity<Void> unfollow(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID followId
  ) {
    UUID followerId = resolveAuthenticatedUserId(userDetails);
    followService.unfollow(followerId, followId);
    return ResponseEntity.noContent().build();
  }

  private UUID resolveAuthenticatedUserId(UserDetails userDetails) {
    if (userDetails == null) {
      throw new FollowForbiddenException();
    }
    return userService.resolveUserId(userDetails.getUsername());
  }
}