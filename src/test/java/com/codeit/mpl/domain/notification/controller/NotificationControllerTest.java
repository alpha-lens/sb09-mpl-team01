package com.codeit.mpl.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.notification.service.NotificationService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.GlobalExceptionHandler;
import com.codeit.mpl.infra.sse.SseService;
import com.codeit.mpl.infra.security.UserPrincipal;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

  private MockMvc mockMvc;

  @Mock
  private NotificationService notificationService;

  @Mock
  private SseService sseService;

  @InjectMocks
  private NotificationController notificationController;

  private UserPrincipal mockUserPrincipal;
  private final UUID testUserId = UUID.fromString("11111111-2222-3333-4444-555555555555");

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

    mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
        .setCustomArgumentResolvers(resolver)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("알림 목록 조회 성공")
  void getNotifications_success() throws Exception {
    // given
    CursorPageResponseDto responseDto = new CursorPageResponseDto(
        Collections.emptyList(), null, null, false, 0L, "createdAt", Direction.DESCENDING
    );
    given(notificationService.getNotifications(any(), any(), any(), any(Integer.class), any()))
        .willReturn(responseDto);

    // when & then
    mockMvc.perform(get("/api/notifications")
            .param("limit", "10")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.hasNext").value(false));

    then(notificationService).should().getNotifications(testUserId, null, null, 10, Direction.DESCENDING);
  }

  @Test
  @DisplayName("알림 목록 조회 실패 - 존재하지 않는 유저")
  void getNotifications_fail_userNotFound() throws Exception {
    // given
    mockUserPrincipal = new UserPrincipal(null, "test@example.com", Collections.emptyList());

    // when & then
    mockMvc.perform(get("/api/notifications")
            .param("limit", "10"))
        .andExpect(status().isNotFound()); // UserNotFoundException으로 404 리턴
  }

  @Test
  @DisplayName("알림 삭제 성공")
  void deleteNotification_success() throws Exception {
    // given
    UUID notificationId = UUID.randomUUID();

    // when & then
    mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId))
        .andExpect(status().isNoContent());

    then(notificationService).should().deleteNotification(notificationId);
  }
}
