package com.codeit.mpl.infra.security;

import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /**
     * Authenticates requests using JWT tokens and checks blacklist and token version.
     * <p>
     * Extracts a token from the request and, if valid, establishes the authenticated
     * principal in the security context before continuing the filter chain.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null) {
            if (jwtTokenProvider.validateToken(token)) {
                // Step 1: Check blacklist (Fail-Open policy is inside jwtUtil)
                if (!jwtUtil.isAccessTokenBlacklisted(token)) {
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    Object principal = authentication.getPrincipal();

                    if (principal instanceof UserPrincipal userPrincipal && userPrincipal.userId() != null) {
                        // Step 2: Check token version (Fail-Open policy is inside jwtUtil)
                        int currentVersion = jwtUtil.getCurrentTokenVersion(userPrincipal.userId());
                        if (userPrincipal.tokenVersion() >= currentVersion) {
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        } else {
                            log.info("Token version stale for user {}: token version={}, current version={}",
                                    userPrincipal.userId(), userPrincipal.tokenVersion(), currentVersion);
                            writeInvalidTokenResponse(response);
                            return;
                        }
                    } else {
                        log.warn("Rejected authentication: missing userId claim or unexpected principal type");
                    }
                } else {
                    log.info("Attempted access with blacklisted token");
                }
            } else if (jwtTokenProvider.isTokenExpired(token)) {
                // Access Token이 만료된 경우: Refresh Token을 확인하여 백엔드에서 자동 갱신 시도
                String refreshToken = resolveRefreshToken(request);
                if (refreshToken != null) {
                    try {
                        UUID userId = jwtUtil.extractUserIdFromRefreshToken(refreshToken);
                        if (jwtUtil.isValidForRotation(userId, refreshToken)) {
                            int currentVersion = jwtUtil.getCurrentTokenVersion(userId);
                            // 신규 Access Token 생성
                            String newAccessToken = jwtTokenProvider.createToken(
                                    jwtTokenProvider.getUsername(token),
                                    "ROLE_USER",
                                    userId.toString(),
                                    currentVersion
                            );
                            Authentication authentication = jwtTokenProvider.getAuthentication(newAccessToken);
                            SecurityContextHolder.getContext().setAuthentication(authentication);

                            // 응답 헤더에 새 Access Token 탑재
                            response.setHeader("Authorization", "Bearer " + newAccessToken);
                            log.debug("Auto-refreshed access token for user {}", userId);
                        }
                    } catch (Exception e) {
                        log.debug("Auto-refresh skipped due to invalid refresh token: {}", e.getMessage());
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Writes a 401 response with the same body shape GlobalExceptionHandler uses for
     * MplException(ErrorCode.INVALID_TOKEN), so the client sees the same error regardless
     * of whether invalidation was detected via /api/auth/refresh or a normal API call.
     */
    private void writeInvalidTokenResponse(HttpServletResponse response) throws IOException {
        ErrorCode errorCode = ErrorCode.INVALID_TOKEN;
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ErrorResponse.of(errorCode)));
    }

    /**
     * Extracts a JWT token from the request.
     *
     * @return The JWT token, or {@code null} if not found.
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken)) {
            if (bearerToken.startsWith("Bearer ")) {
                return bearerToken.substring(7).trim();
            }
            if (bearerToken.startsWith("Bearer")) {
                return bearerToken.substring(6).trim();
            }
        }
        
        // SSE나 WebSocket 등 특정 요청에 대해 Query Parameter로 토큰이 들어올 때의 대체 추출 지원
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        
        return null;
    }

    /**
     * Extracts a Refresh Token from request cookies or headers.
     */
    private String resolveRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName()) && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        String refreshHeader = request.getHeader("Refresh-Token");
        if (StringUtils.hasText(refreshHeader)) {
            return refreshHeader.trim();
        }
        return null;
    }
}
