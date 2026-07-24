package com.codeit.mpl.domain.user.controller;

import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.dto.response.UserDto;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.JwtDto;
import com.codeit.mpl.infra.exception.GlobalExceptionHandler;
import com.codeit.mpl.infra.security.JwtUtil;
import com.codeit.mpl.infra.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @Mock
  private UserService userService;

  @Mock
  private JwtUtil jwtUtil;

  @Mock
  private HttpServletRequest servletRequest;

  private AuthController authController;

  private UUID userId;
  private UserDto userDto;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    userDto = new UserDto(userId, Instant.now(), "test@test.com", "테스트유저", null, UserRole.USER, false);
    authController = new AuthController(userService, jwtUtil, servletRequest);

    mockMvc = MockMvcBuilders.standaloneSetup(authController)
        .setControllerAdvice(new GlobalExceptionHandler())
        .build();
  }

  @Test
  @DisplayName("폼 로그인 성공")
  void signInForm_success() throws Exception {
    SignInResult result = new SignInResult(new JwtDto(userDto, "access-token"), "refresh-token");
    given(userService.signIn(any(SignInRequest.class))).willReturn(result);
    given(jwtUtil.getRefreshExpirationMs()).willReturn(604800000L);

    mockMvc.perform(post("/api/auth/sign-in")
            .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
            .param("username", "test@test.com")
            .param("password", "password1234"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").value("access-token"));
  }

  @Test
  @DisplayName("로그아웃 - 인증된 사용자 + Authorization 헤더가 있으면 accessToken을 넘겨 로그아웃 처리한다")
  void signOut_withPrincipalAndBearerToken() {
    UserPrincipal principal = new UserPrincipal(userId, "test@test.com", java.util.List.<GrantedAuthority>of());
    given(servletRequest.getHeader("Authorization")).willReturn("Bearer access-token-value");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseEntity<Void> result = authController.signOut(principal, null, response);

    assertThat(result.getStatusCode().value()).isEqualTo(204);
    then(userService).should().signOut(userId, "access-token-value");
    assertThat(response.getHeader("Set-Cookie")).isNotNull();
  }

  @Test
  @DisplayName("로그아웃 - Authorization 헤더가 Bearer 형식이 아니면 accessToken 없이 로그아웃 처리한다")
  void signOut_withPrincipal_nonBearerHeader() {
    UserPrincipal principal = new UserPrincipal(userId, "test@test.com", java.util.List.<GrantedAuthority>of());
    given(servletRequest.getHeader("Authorization")).willReturn("Basic something");
    MockHttpServletResponse response = new MockHttpServletResponse();

    authController.signOut(principal, null, response);

    then(userService).should().signOut(userId, null);
  }

  @Test
  @DisplayName("로그아웃 - principal이 없어도 유효한 refresh token 쿠키가 있으면 그걸로 userId를 복구해 로그아웃 처리한다")
  void signOut_withoutPrincipal_resolvesUserIdFromRefreshToken() {
    given(servletRequest.getHeader("Authorization")).willReturn(null);
    given(jwtUtil.extractUserIdFromRefreshToken("refresh-token-value")).willReturn(userId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    authController.signOut(null, "refresh-token-value", response);

    then(userService).should().signOut(userId, null);
  }

  @Test
  @DisplayName("로그아웃 - principal도 없고 refresh token 파싱도 실패하면 signOut 서비스 호출 없이 쿠키만 삭제한다")
  void signOut_withoutPrincipal_invalidRefreshToken_skipsServiceCall() {
    given(servletRequest.getHeader("Authorization")).willReturn(null);
    given(jwtUtil.extractUserIdFromRefreshToken("bad-token"))
        .willThrow(new RuntimeException("invalid token"));
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseEntity<Void> result = authController.signOut(null, "bad-token", response);

    assertThat(result.getStatusCode().value()).isEqualTo(204);
    then(userService).should(never()).signOut(any(), any());
    assertThat(response.getHeader("Set-Cookie")).isNotNull();
  }

  @Test
  @DisplayName("로그아웃 - principal도 refresh token도 없으면 signOut 서비스를 호출하지 않는다")
  void signOut_withoutPrincipalAndRefreshToken_skipsServiceCall() {
    given(servletRequest.getHeader("Authorization")).willReturn(null);
    MockHttpServletResponse response = new MockHttpServletResponse();

    authController.signOut(null, null, response);

    then(userService).should(never()).signOut(any(), any());
    then(jwtUtil).should(never()).extractUserIdFromRefreshToken(any());
  }

  @Test
  @DisplayName("비밀번호 초기화 요청 성공")
  void resetPassword_success() throws Exception {
    ResetPasswordRequest request = new ResetPasswordRequest("test@test.com");

    mockMvc.perform(post("/api/auth/reset-password")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNoContent());

    then(userService).should().resetPassword(eq(request));
  }

  @Test
  @DisplayName("토큰 재발급 성공")
  void refresh_success() {
    SignInResult result = new SignInResult(new JwtDto(userDto, "new-access-token"), "new-refresh-token");
    given(userService.refresh("old-refresh-token")).willReturn(result);
    given(jwtUtil.getRefreshExpirationMs()).willReturn(604800000L);
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseEntity<JwtDto> result2 = authController.refresh("old-refresh-token", response);

    assertThat(result2.getBody().accessToken()).isEqualTo("new-access-token");
    assertThat(response.getHeader("Set-Cookie")).isNotNull();
  }

  @Test
  @DisplayName("CSRF 토큰 조회 성공 - 토큰이 있으면 정보를 그대로 내려준다")
  void csrfToken_success() {
    CsrfToken csrfToken = mock(CsrfToken.class);
    given(csrfToken.getHeaderName()).willReturn("X-XSRF-TOKEN");
    given(csrfToken.getParameterName()).willReturn("_csrf");
    given(csrfToken.getToken()).willReturn("csrf-token-value");

    ResponseEntity<Map<String, String>> result = authController.csrfToken(csrfToken);

    assertThat(result.getStatusCode().value()).isEqualTo(200);
    assertThat(result.getBody()).containsEntry("token", "csrf-token-value");
  }

  @Test
  @DisplayName("CSRF 토큰 조회 - CSRF가 비활성화되어 토큰이 없으면 204를 반환한다")
  void csrfToken_null_returnsNoContent() {
    ResponseEntity<Map<String, String>> result = authController.csrfToken(null);

    assertThat(result.getStatusCode().value()).isEqualTo(204);
    assertThat(result.getBody()).isNull();
  }
}
