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
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            // Step 1: Check Blacklist (Fail-Open policy is inside jwtUtil)
            if (!jwtUtil.isAccessTokenBlacklisted(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                Object principalObj = authentication.getPrincipal();

                if (principalObj instanceof UserPrincipal userPrincipal) {
                    // Step 2: Check Token Version (Fail-Open policy is inside jwtUtil)
                    int currentVersion = jwtUtil.getCurrentTokenVersion(userPrincipal.userId());

                    if (userPrincipal.tokenVersion() > currentVersion) {
                        log.warn("Anomaly detected: Token version ({}) is greater than current cached version ({}) for user {}",
                                userPrincipal.tokenVersion(), currentVersion, userPrincipal.userId());
                    }

                    if (userPrincipal.tokenVersion() >= currentVersion) {
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    } else {
                        log.info("Token version stale for user {}: token version={}, current version={}",
                                userPrincipal.userId(), userPrincipal.tokenVersion(), currentVersion);
                    }
                } else {
                    SecurityContextHolder.getContext().setAuthentication(authentication);
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
        
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            return tokenParam;
        }
        
        return null;
    }
}
