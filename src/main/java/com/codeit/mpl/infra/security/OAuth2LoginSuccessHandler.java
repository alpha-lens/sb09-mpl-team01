package com.codeit.mpl.infra.security;

import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @Value("${mpl.frontend.base-url}")
    private String frontendBaseUrl;

    // 프론트엔드 코드를 건드릴 수 없어서 accessToken을 URL로 넘기는 별도 콜백 라우트를 못 둔다.
    // 대신 REFRESH_TOKEN 쿠키만 심어서 홈으로 보내면, 기존 SPA가 이미 갖고 있는
    // "쿠키로 /api/auth/refresh 호출해 세션 복원" 로직이 그대로 로그인 처리를 해준다.
    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        CustomOAuth2User principal = (CustomOAuth2User) authentication.getPrincipal();
        SignInResult result = userService.issueTokens(principal.userId());
        log.info("OAuth2 로그인 성공 - userId={}", principal.userId());

        CookieUtil.setRefreshTokenCookie(response, result.refreshToken(), jwtUtil.getRefreshExpirationMs() / 1000);

        response.sendRedirect(frontendBaseUrl + "/");
    }
}
