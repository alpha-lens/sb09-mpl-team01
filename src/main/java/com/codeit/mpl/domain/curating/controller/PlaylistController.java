package com.codeit.mpl.domain.curating.controller;

import com.codeit.mpl.domain.curating.controller.api.PlaylistApi;
import com.codeit.mpl.domain.curating.dto.request.PlaylistCreateRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistSearchRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistUpdateRequest;
import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.service.PlaylistService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playlists")
public class PlaylistController implements PlaylistApi {

  private final PlaylistService playlistService;

  @GetMapping
  public ResponseEntity<CursorPageResponseDto<PlaylistDto>> getPlaylists(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @ModelAttribute PlaylistSearchRequest request
  ) {
    UUID currentUserId = userPrincipal != null ? userPrincipal.userId() : null;

    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        request.getKeywordLike(),
        request.getOwnerIdEqual(),
        request.getSubscriberIdEqual(),
        request.getCursor(),
        request.getIdAfter(),
        request.getLimit(),
        request.getSortBy(),
        request.getSortDirection(),
        currentUserId
    );
    return ResponseEntity.ok(response);
  }

  @PostMapping
  public ResponseEntity<PlaylistDto> createPlaylist(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @Valid @RequestBody PlaylistCreateRequest request
  ) {
    UUID ownerId = resolveAuthenticatedUserId(userPrincipal);
    PlaylistDto response = playlistService.createPlaylist(ownerId, request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> getPlaylist(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId
  ) {
    UUID currentUserId = userPrincipal != null ? userPrincipal.userId() : null;
    PlaylistDto response = playlistService.getPlaylist(playlistId, currentUserId);
    return ResponseEntity.ok(response);
  }

  @PatchMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> updatePlaylist(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request
  ) {
    UUID ownerId = resolveAuthenticatedUserId(userPrincipal);
    PlaylistDto response = playlistService.updatePlaylist(ownerId, playlistId, request);
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> deletePlaylist(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId
  ) {
    UUID ownerId = resolveAuthenticatedUserId(userPrincipal);
    playlistService.deletePlaylist(ownerId, playlistId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> addContent(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId
  ) {
    UUID ownerId = resolveAuthenticatedUserId(userPrincipal);
    playlistService.addContent(ownerId, playlistId, contentId);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> removeContent(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId
  ) {
    UUID ownerId = resolveAuthenticatedUserId(userPrincipal);
    playlistService.removeContent(ownerId, playlistId, contentId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> subscribePlaylist(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId
  ) {
    UUID subscriberId = resolveAuthenticatedUserId(userPrincipal);
    playlistService.subscribePlaylist(subscriberId, playlistId);
    return ResponseEntity.ok().build();
  }

  @DeleteMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> unsubscribePlaylist(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID playlistId
  ) {
    UUID subscriberId = resolveAuthenticatedUserId(userPrincipal);
    playlistService.unsubscribePlaylist(subscriberId, playlistId);
    return ResponseEntity.noContent().build();
  }

  private UUID resolveAuthenticatedUserId(UserPrincipal userPrincipal) {
    if (userPrincipal == null) {
      throw new MplException(ErrorCode.INVALID_TOKEN);
    }
    UUID userId = userPrincipal.userId();
    if (userId == null) {
      throw new MplException(ErrorCode.INVALID_TOKEN);
    }
    return userId;
  }
}