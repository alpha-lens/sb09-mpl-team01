package com.codeit.mpl.domain.user.controller;

import com.codeit.mpl.domain.user.controller.api.AuthApi;
import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.JwtDto;
import com.codeit.mpl.infra.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.codeit.mpl.infra.security.UserPrincipal;
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
        log.info("signIn (Form)");
        SignInResult result = userService.signIn(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.jwtDto());
    }

    @PostMapping("/sign-out")
    @Override
    public ResponseEntity<Void> signOut(@AuthenticationPrincipal UserPrincipal userPrincipal, HttpServletResponse response) {
        String bearerToken = request.getHeader("Authorization");
        String token = null;
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }
        if (userPrincipal != null && userPrincipal.userId() != null) {
            userService.signOut(userPrincipal.userId(), token);
        }
        deleteRefreshTokenCookie(response);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Override
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/refresh")
    @Override
    public ResponseEntity<JwtDto> refresh(
            @CookieValue(name = "REFRESH_TOKEN", required = false) String refreshToken,
            HttpServletResponse response) {
        SignInResult result = userService.refresh(refreshToken);
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.jwtDto());
    }

    @GetMapping("/csrf-token")
    @Override
    public ResponseEntity<Map<String, String>> csrfToken(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of(
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName(),
                "token", csrfToken.getToken()
        ));
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = ResponseCookie.from("REFRESH_TOKEN", token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(jwtUtil.getRefreshExpirationMs() / 1000)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("REFRESH_TOKEN", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
