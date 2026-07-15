package com.codeit.mpl.curating.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.curating.controller.PlaylistController;
import com.codeit.mpl.domain.curating.dto.request.PlaylistCreateRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistUpdateRequest;
import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.service.PlaylistService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class) // 스프링 대신 Mockito 환경 사용!
class PlaylistControllerTest {

  private MockMvc mockMvc;
  private ObjectMapper objectMapper = new ObjectMapper();

  @Mock // @MockBean 대신 순수 Mock 사용
  private PlaylistService playlistService;

  @InjectMocks
  private PlaylistController playlistController;

  private UUID currentUserId;
  private UserPrincipal userPrincipal;

  @BeforeEach
  void setUp() {
    currentUserId = UUID.randomUUID();
    userPrincipal = mock(UserPrincipal.class);
    when(userPrincipal.userId()).thenReturn(currentUserId);

    // MockMvc 수동 조립 및 @AuthenticationPrincipal 가짜 주입기 설정
    mockMvc = MockMvcBuilders.standaloneSetup(playlistController)
        .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
          @Override
          public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType().equals(UserPrincipal.class);
          }

          @Override
          public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
              NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            return userPrincipal;
          }
        })
        .build();
  }

  @Test
  @DisplayName("GET /api/playlists - 플레이리스트 목록 조회 성공")
  void getPlaylists() throws Exception {
    CursorPageResponseDto<PlaylistDto> response = new CursorPageResponseDto<>(
        List.of(), null, null, false, 0, "createdAt", null
    );

    // 💡 수정됨: 6번째 인자(limit)가 기본형 int이므로 any() 대신 org.mockito.ArgumentMatchers.anyInt()를 사용합니다.
    when(playlistService.getPlaylists(
        any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt(), any(), any(), eq(currentUserId)))
        .thenReturn(response);

    mockMvc.perform(get("/api/playlists")
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("POST /api/playlists - 플레이리스트 생성 성공")
  void createPlaylist() throws Exception {
    // 수정됨: 뒤에 있던 boolean 인자(true)를 제거했습니다. (2개의 인자만 전달)
    PlaylistCreateRequest request = new PlaylistCreateRequest("새 플레이리스트", "설명");
    PlaylistDto responseDto = mock(PlaylistDto.class);

    when(playlistService.createPlaylist(eq(currentUserId), any(PlaylistCreateRequest.class)))
        .thenReturn(responseDto);

    mockMvc.perform(post("/api/playlists")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("GET /api/playlists/{playlistId} - 단일 플레이리스트 조회 성공")
  void getPlaylist() throws Exception {
    UUID playlistId = UUID.randomUUID();
    PlaylistDto responseDto = mock(PlaylistDto.class);

    when(playlistService.getPlaylist(eq(playlistId), eq(currentUserId)))
        .thenReturn(responseDto);

    mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("PATCH /api/playlists/{playlistId} - 플레이리스트 수정 성공")
  void updatePlaylist() throws Exception {
    UUID playlistId = UUID.randomUUID();
    // 💡 수정됨: 뒤에 있던 boolean 인자(false)를 제거했습니다. (2개의 인자만 전달)
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("수정된 제목", "수정된 설명");
    PlaylistDto responseDto = mock(PlaylistDto.class);

    when(playlistService.updatePlaylist(eq(currentUserId), eq(playlistId), any(PlaylistUpdateRequest.class)))
        .thenReturn(responseDto);

    mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("DELETE /api/playlists/{playlistId} - 플레이리스트 삭제 성공")
  void deletePlaylist() throws Exception {
    UUID playlistId = UUID.randomUUID();

    mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(playlistService).deletePlaylist(currentUserId, playlistId);
  }

  @Test
  @DisplayName("POST /api/playlists/{playlistId}/contents/{contentId} - 콘텐츠 추가 성공")
  void addContent() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    mockMvc.perform(post("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(playlistService).addContent(currentUserId, playlistId, contentId);
  }

  @Test
  @DisplayName("DELETE /api/playlists/{playlistId}/contents/{contentId} - 콘텐츠 제거 성공")
  void removeContent() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    mockMvc.perform(delete("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(playlistService).removeContent(currentUserId, playlistId, contentId);
  }

  @Test
  @DisplayName("POST /api/playlists/{playlistId}/subscription - 플레이리스트 구독 성공")
  void subscribePlaylist() throws Exception {
    UUID playlistId = UUID.randomUUID();

    mockMvc.perform(post("/api/playlists/{playlistId}/subscription", playlistId)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verify(playlistService).subscribePlaylist(currentUserId, playlistId);
  }

  @Test
  @DisplayName("DELETE /api/playlists/{playlistId}/subscription - 플레이리스트 구독 취소 성공")
  void unsubscribePlaylist() throws Exception {
    UUID playlistId = UUID.randomUUID();

    mockMvc.perform(delete("/api/playlists/{playlistId}/subscription", playlistId)
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isNoContent());

    verify(playlistService).unsubscribePlaylist(currentUserId, playlistId);
  }
}