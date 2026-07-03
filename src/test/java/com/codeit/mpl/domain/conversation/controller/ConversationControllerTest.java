package com.codeit.mpl.domain.conversation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.service.ConversationService;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.GlobalExceptionHandler;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

  private MockMvc mockMvc;

  @Mock
  private ConversationService conversationService;

  @InjectMocks
  private ConversationController conversationController;

  private UserPrincipal mockUserPrincipal;
  private final UUID testUserId = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    mockUserPrincipal = new UserPrincipal(testUserId, "test@example.com", Collections.emptyList());

    HandlerMethodArgumentResolver resolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return mockUserPrincipal;
      }
    };

    mockMvc = MockMvcBuilders.standaloneSetup(conversationController)
        .setCustomArgumentResolvers(resolver)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("대화방 목록 조회 성공")
  void getConversations_success() throws Exception {
    // given
    ConversationDto conversationDto = new ConversationDto(
        UUID.randomUUID(),
        new UserSummary(UUID.randomUUID(), "상대방", null),
        new DirectMessageDto(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, "안녕"),
        false
    );
    CursorPageResponseDto<ConversationDto> responseDto = new CursorPageResponseDto<>(
        List.of(conversationDto),
        null,
        null,
        false,
        1L,
        "createdAt",
        Direction.DESCENDING
    );
    given(conversationService.getConversations(eq(testUserId), any(), any()))
        .willReturn(responseDto);

    // when & then
    mockMvc.perform(get("/api/conversations")
            .param("limit", "10"))
        .andExpect(status().isOk());

    then(conversationService).should().getConversations(eq(testUserId), any(), any());
  }

  @Test
  @DisplayName("대화방 생성 성공")
  void createConversation_success() throws Exception {
    // given
    UUID otherUserId = UUID.randomUUID();
    ConversationCreateRequest request = new ConversationCreateRequest(otherUserId);

    ConversationDto conversationDto = new ConversationDto(
        UUID.randomUUID(),
        new UserSummary(otherUserId, "상대방", null),
        new DirectMessageDto(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null, null, "안녕"),
        false
    );
    given(conversationService.createConversation(eq(testUserId), any(ConversationCreateRequest.class)))
        .willReturn(conversationDto);

    // when & then
    mockMvc.perform(post("/api/conversations")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists());

    then(conversationService).should().createConversation(eq(testUserId), any(ConversationCreateRequest.class));
  }

  @Test
  @DisplayName("메시지 읽음 처리 성공")
  void readConversationMessage_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();

    // when & then
    mockMvc.perform(post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
            conversationId, directMessageId))
        .andExpect(status().isOk());

    then(conversationService).should().readConversationMessages(conversationId, directMessageId, testUserId);
  }

  @Test
  @DisplayName("대화방 단건 조회 성공")
  void getConversation_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    ConversationDto conversationDto = new ConversationDto(
        conversationId,
        new UserSummary(UUID.randomUUID(), "상대방", null),
        null,
        false
    );
    given(conversationService.getConversationDto(conversationId, testUserId))
        .willReturn(conversationDto);

    // when & then
    mockMvc.perform(get("/api/conversations/{conversationId}", conversationId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(conversationId.toString()));

    then(conversationService).should().getConversationDto(conversationId, testUserId);
  }

  @Test
  @DisplayName("대화방 메시지 목록 조회 성공")
  void getDirectMessage_success() throws Exception {
    // given
    UUID conversationId = UUID.randomUUID();
    CursorPageResponseDto responseDto = new CursorPageResponseDto(
        Collections.emptyList(), null, null, false, 0L, "createdAt", Direction.DESCENDING
    );
    given(conversationService.getDirectMessages(eq(conversationId), eq(testUserId), any()))
        .willReturn(responseDto);

    // when & then
    mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
            .param("limit", "10"))
        .andExpect(status().isOk());

    then(conversationService).should().getDirectMessages(eq(conversationId), eq(testUserId), any());
  }

  @Test
  @DisplayName("상대 유저와의 대화방 조회 성공")
  void getWith_success() throws Exception {
    // given
    UUID targetUserId = UUID.randomUUID();
    ConversationDto conversationDto = new ConversationDto(
        UUID.randomUUID(),
        new UserSummary(targetUserId, "상대방", null),
        null,
        false
    );
    given(conversationService.getWith(testUserId, targetUserId)).willReturn(conversationDto);

    // when & then
    mockMvc.perform(get("/api/conversations/with")
            .param("userId", targetUserId.toString()))
        .andExpect(status().isOk());

    then(conversationService).should().getWith(testUserId, targetUserId);
  }

  @Test
  @DisplayName("상대 유저와의 대화방 조회 실패 - 대화방 없음")
  void getWith_notFound() throws Exception {
    // given
    UUID targetUserId = UUID.randomUUID();
    given(conversationService.getWith(testUserId, targetUserId)).willReturn(null);

    // when & then
    mockMvc.perform(get("/api/conversations/with")
            .param("userId", targetUserId.toString()))
        .andExpect(status().isNotFound());

    then(conversationService).should().getWith(testUserId, targetUserId);
  }
}
