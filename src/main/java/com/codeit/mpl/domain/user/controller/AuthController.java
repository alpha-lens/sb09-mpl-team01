package com.codeit.mpl.domain.user.controller;

import com.codeit.mpl.domain.user.controller.api.AuthApi;
import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.JwtDto;
import com.codeit.mpl.infra.security.CookieUtil;
import com.codeit.mpl.infra.security.JwtUtil;
import com.codeit.mpl.infra.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController implements AuthApi {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final HttpServletRequest request;

    @PostMapping(value = "/sign-in", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Override
    public ResponseEntity<JwtDto> signInForm(@Valid SignInRequest request, HttpServletResponse response) {
        log.info("[Auth API] POST /api/auth/sign-in 요청 - username={}", request.username());
        SignInResult result = userService.signIn(request);
        CookieUtil.setRefreshTokenCookie(response, result.refreshToken(), jwtUtil.getRefreshExpirationMs() / 1000);
        return ResponseEntity.ok(result.jwtDto());
    }

    @PostMapping("/sign-out")
    @Override
    public ResponseEntity<Void> signOut(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken,
            HttpServletResponse response) {
        String bearerToken = request.getHeader("Authorization");
        String accessToken = null;
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            accessToken = bearerToken.substring(7);
        }

        // access token이 이미 만료/누락돼 principal이 없더라도, 유효한 refresh token 쿠키가
        // 남아있다면 서버 측 세션(Redis)은 반드시 무효화해야 한다 - 그렇지 않으면 로그아웃 API가
        // 204를 반환하고도 실제로는 세션이 살아있는 상태가 된다.
        UUID userId = userPrincipal != null ? userPrincipal.userId() : null;
        if (userId == null && refreshToken != null) {
            try {
                userId = jwtUtil.extractUserIdFromRefreshToken(refreshToken);
            } catch (Exception e) {
                log.debug("Failed to resolve userId from refresh token on sign-out: {}", e.getMessage());
            }
        }
        log.info("[Auth API] POST /api/auth/sign-out 요청 - userId={}", userId);
        if (userId != null) {
            userService.signOut(userId, accessToken);
        }
        CookieUtil.deleteRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Override
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("[Auth API] POST /api/auth/reset-password 요청 - email={}", request.email());
        userService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Override
    public ResponseEntity<JwtDto> refresh(
            @CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken,
            HttpServletResponse response) {
        log.debug("[Auth API] POST /api/auth/refresh 요청");
        SignInResult result = userService.refresh(refreshToken);
        CookieUtil.setRefreshTokenCookie(response, result.refreshToken(), jwtUtil.getRefreshExpirationMs() / 1000);
        return ResponseEntity.ok(result.jwtDto());
    }

    @GetMapping("/csrf-token")
    @Override
    public ResponseEntity<Map<String, String>> csrfToken(CsrfToken csrfToken) {
        if (csrfToken == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken()
        ));
    }
}
