package com.codeit.mpl.infra.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

public final class CookieUtil {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";

    private CookieUtil() {
    }

    public static void setRefreshTokenCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(maxAgeSeconds)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static void deleteRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
