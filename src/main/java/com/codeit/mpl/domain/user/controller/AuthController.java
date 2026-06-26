package com.codeit.mpl.domain.user.controller;

import com.codeit.mpl.domain.user.controller.api.AuthApi;
import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.domain.user.dto.response.SignInResult;
import com.codeit.mpl.domain.user.service.UserService;
import com.codeit.mpl.infra.common.dto.JwtDto;
import com.codeit.mpl.infra.security.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController implements AuthApi {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/sign-in")
    @Override
    public ResponseEntity<JwtDto> signIn(@Valid @RequestBody SignInRequest request, HttpServletResponse response) {
        SignInResult result = userService.signIn(request);
        setRefreshTokenCookie(response, result.refreshToken());
        return ResponseEntity.ok(result.jwtDto());
    }

    @PostMapping("/sign-out")
    @Override
    public ResponseEntity<Void> signOut(@AuthenticationPrincipal UserDetails userDetails, HttpServletResponse response) {
        userService.signOut(userService.resolveUserId(userDetails.getUsername()));
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
    public ResponseEntity<Void> csrfToken() {
        return ResponseEntity.noContent().build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie("REFRESH_TOKEN", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtUtil.getRefreshExpirationMs() / 1000));
        response.addCookie(cookie);
    }

    private void deleteRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie("REFRESH_TOKEN", "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
