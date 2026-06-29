package com.codeit.mpl.domain.curating.controller.api;

import com.codeit.mpl.domain.curating.dto.request.PlaylistCreateRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistSearchRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistUpdateRequest;
import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "플레이리스트 관리", description = "플레이리스트 CRUD 및 구독, 콘텐츠 추가/삭제 API")
public interface PlaylistApi {

  @Operation(summary = "플레이리스트 목록 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공")
  })
  ResponseEntity<CursorPageResponseDto<PlaylistDto>> getPlaylists(
      @ModelAttribute PlaylistSearchRequest request
  );

  @Operation(summary = "플레이리스트 생성")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "생성 성공"),
      @ApiResponse(responseCode = "400", description = "입력값 검증 실패")
  })
  ResponseEntity<PlaylistDto> createPlaylist(@Valid @RequestBody PlaylistCreateRequest request);

  @Operation(summary = "플레이리스트 단건 조회")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "조회 성공"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 없음")
  })
  ResponseEntity<PlaylistDto> getPlaylist(@PathVariable UUID playlistId);

  @Operation(summary = "플레이리스트 수정")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "수정 성공"),
      @ApiResponse(responseCode = "403", description = "권한 없음"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 없음")
  })
  ResponseEntity<PlaylistDto> updatePlaylist(
      @PathVariable UUID playlistId,
      @Valid @RequestBody PlaylistUpdateRequest request
  );

  @Operation(summary = "플레이리스트 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공"),
      @ApiResponse(responseCode = "403", description = "권한 없음"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 없음")
  })
  ResponseEntity<Void> deletePlaylist(@PathVariable UUID playlistId);

  @Operation(summary = "플레이리스트에 콘텐츠 추가")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "추가 성공"),
      @ApiResponse(responseCode = "403", description = "권한 없음"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 또는 콘텐츠 없음")
  })
  ResponseEntity<Void> addContent(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId
  );

  @Operation(summary = "플레이리스트에서 콘텐츠 삭제")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "삭제 성공"),
      @ApiResponse(responseCode = "403", description = "권한 없음"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 또는 콘텐츠 없음")
  })
  ResponseEntity<Void> removeContent(
      @PathVariable UUID playlistId,
      @PathVariable UUID contentId
  );

  @Operation(summary = "플레이리스트 구독")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "구독 성공"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 없음")
  })
  ResponseEntity<Void> subscribePlaylist(@PathVariable UUID playlistId);

  @Operation(summary = "플레이리스트 구독 취소")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "구독 취소 성공"),
      @ApiResponse(responseCode = "404", description = "플레이리스트 없음")
  })
  ResponseEntity<Void> unsubscribePlaylist(@PathVariable UUID playlistId);
}