package com.codeit.mpl.infra.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtUtil jwtUtil;

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

        if (token != null && jwtTokenProvider.validateToken(token)) {
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
                    }
                } else {
                    // userId 클레임이 없는 레거시/손상된 토큰이거나 예상치 못한 principal 타입이면,
                    // 세션 무효화 검증을 우회할 수 있으므로 인증을 열어주지 않고 닫힌 방향으로 처리한다.
                    log.warn("Rejected authentication: missing userId claim or unexpected principal type");
                }
            } else {
                log.info("Attempted access with blacklisted token");
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts a JWT token from the request.
     *
     * @return The JWT token, or {@code null} if not found.
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // SSE나 WebSocket 등 특정 요청에 대해 Query Parameter로 토큰이 들어올 때의 대체 추출 지원
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        
        return null;
    }
}
