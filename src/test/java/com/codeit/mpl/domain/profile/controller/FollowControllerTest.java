package com.codeit.mpl.domain.profile.controller;

import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codeit.mpl.domain.profile.dto.request.FollowRequest;
import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.service.FollowService;
import com.codeit.mpl.infra.exception.GlobalExceptionHandler;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
class FollowControllerTest {


  private MockMvc mockMvc;


  // FollowController가 ObjectMapper를 생성자 주입(@RequiredArgsConstructor)으로 받으므로
  // @Mock(null 반환)이 아닌 @Spy(실제 인스턴스)로 선언해야 getFollowedByMe()에서 NPE가 발생하지 않습니다.
  @Spy
  private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Mock
  private FollowService followService;

  @InjectMocks
  private FollowController followController;

  private UserPrincipal userPrincipal;
  private UUID followerId;

  @BeforeEach
  void setUp() {
    followerId = UUID.randomUUID();
    // UserPrincipal record: (UUID userId, String email, authorities, int tokenVersion)
    // tokenVersion은 int 타입이므로 반드시 정수값(0 이상)으로 명시해야 합니다.
    userPrincipal = new UserPrincipal(followerId, "test@example.com", List.of(), 1);

    // @AuthenticationPrincipal UserPrincipal 파라미터를 위한 ArgumentResolver
    HandlerMethodArgumentResolver principalResolver = new HandlerMethodArgumentResolver() {
      @Override
      public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
            && parameter.getParameterType().equals(UserPrincipal.class);
      }

      @Override
      public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
          NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return userPrincipal;
      }
    };

    mockMvc = MockMvcBuilders.standaloneSetup(followController)
        .setCustomArgumentResolvers(principalResolver)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("POST /api/follows - 팔로우 요청 성공")
  void follow_success() throws Exception {
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    FollowRequest request = new FollowRequest(followeeId);
    FollowDto followDto = new FollowDto(followId, followeeId, followerId);

    given(followService.follow(eq(followerId), eq(followeeId))).willReturn(followDto);

    mockMvc.perform(post("/api/follows")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followerId").value(followerId.toString()));
  }

  @Test
  @DisplayName("DELETE /api/follows/{followId} - 언팔로우 요청 성공")
  void unfollow_success() throws Exception {
    UUID followId = UUID.randomUUID();

    willDoNothing().given(followService).unfollow(eq(followerId), eq(followId));

    mockMvc.perform(delete("/api/follows/{followId}", followId))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("GET /api/follows/followed-by-me - 팔로우 중인 경우 데이터 반환")
  void getFollowedByMe_following() throws Exception {
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    FollowDto followDto = new FollowDto(followId, followeeId, followerId);
    given(followService.getFollowedByMe(eq(followerId), eq(followeeId))).willReturn(followDto);

    mockMvc.perform(get("/api/follows/followed-by-me")
            .param("followeeId", followeeId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followerId").value(followerId.toString()));
  }

  @Test
  @DisplayName("GET /api/follows/followed-by-me - 팔로우 중이 아닌 경우 'null' 문자열 반환")
  void getFollowedByMe_notFollowing() throws Exception {
    UUID followeeId = UUID.randomUUID();

    // 팔로우 관계 없을 때 service는 null 반환 → controller는 "null" 문자열로 직렬화
    given(followService.getFollowedByMe(eq(followerId), eq(followeeId))).willReturn(null);

    mockMvc.perform(get("/api/follows/followed-by-me")
            .param("followeeId", followeeId.toString()))
        .andExpect(status().isOk())
        .andExpect(content().string("null"));
  }
}
