package com.codeit.mpl.infra.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Value("${mpl.frontend.base-url}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        log.warn("OAuth2 로그인 실패: {}", exception.getMessage());
        String errorMessage = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);
        response.sendRedirect(frontendBaseUrl + "/#/sign-in?error=oauth_failed&error_message=" + errorMessage);
    }
}
