package com.codeit.mpl.domain.curating.controller;

import com.codeit.mpl.domain.curating.controller.api.PlaylistApi;
import com.codeit.mpl.domain.curating.dto.request.PlaylistCreateRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistSearchRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistUpdateRequest;
import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.service.PlaylistService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playlists")
public class PlaylistController implements PlaylistApi {

  private final PlaylistService playlistService;

  private static final UUID TEMP_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

  // 플레이리스트 목록 조회
  @GetMapping
  public ResponseEntity<CursorPageResponseDto<PlaylistDto>> getPlaylists(
      @ModelAttribute PlaylistSearchRequest request
  ) {
    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        request.getKeywordLike(),
        request.getOwnerIdEqual(),
        request.getSubscriberIdEqual(),
        request.getCursor(),
        request.getIdAfter(),
        request.getLimit(),
        request.getSortBy(),
        request.getSortDirection()
    );
    return ResponseEntity.ok(response);
  }

  // 플레이리스트 생성
  @PostMapping
  public ResponseEntity<PlaylistDto> createPlaylist(@Valid @RequestBody PlaylistCreateRequest request) {
    PlaylistDto response = playlistService.createPlaylist(TEMP_USER_ID, request);
    return ResponseEntity.ok(response);
  }

  // 플레이리스트 단건 조회
  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> getPlaylist(@PathVariable UUID playlistId) {
    PlaylistDto response = playlistService.getPlaylist(playlistId);
    return ResponseEntity.ok(response);
  }

  // 플레이리스트 수정
  @PatchMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> updatePlaylist(
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request
  ) {
    PlaylistDto response = playlistService.updatePlaylist(TEMP_USER_ID, playlistId, request);
    return ResponseEntity.ok(response);
  }

  // 플레이리스트 삭제
  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> deletePlaylist(@PathVariable UUID playlistId) {
    playlistService.deletePlaylist(TEMP_USER_ID, playlistId);
    return ResponseEntity.noContent().build();
  }

  // 콘텐츠 추가
  @PostMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> addContent(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId
  ) {
    playlistService.addContent(TEMP_USER_ID, playlistId, contentId);
    return ResponseEntity.ok().build();
  }

  // 콘텐츠 삭제
  @DeleteMapping("/{playlistId}/contents/{contentId}")
  public ResponseEntity<Void> removeContent(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId
  ) {
    playlistService.removeContent(TEMP_USER_ID, playlistId, contentId);
    return ResponseEntity.noContent().build();
  }

  // 플레이리스트 구독
  @PostMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> subscribePlaylist(@PathVariable UUID playlistId) {
    playlistService.subscribePlaylist(TEMP_USER_ID, playlistId);
    return ResponseEntity.ok().build();
  }

  // 플레이리스트 구독 취소
  @DeleteMapping("/{playlistId}/subscription")
  public ResponseEntity<Void> unsubscribePlaylist(@PathVariable UUID playlistId) {
    playlistService.unsubscribePlaylist(TEMP_USER_ID, playlistId);
    return ResponseEntity.noContent().build();
  }
}