package com.codeit.mpl.infra.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration:900000}") // 15 minutes
    private long validityInMilliseconds;

    private Key key;

    /**
     * Initializes the HMAC signing key derived from the configured secret.
     */
    @PostConstruct
    protected void init() {
        this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a JWT token with the specified username, role, userId, and token version.
     *
     * @return a compact JWT string
     */
    public String createToken(String username, String role, String userId, int tokenVersion) {
        Claims claims = Jwts.claims().setSubject(username);
        claims.put("role", role);
        claims.put("userId", userId);
        claims.put("version", tokenVersion);

        Date now = new Date();
        Date validity = new Date(now.getTime() + validityInMilliseconds);

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(validity)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Constructs an Authentication object from a JWT token.
     * If the token does not contain a role claim, the default role "ROLE_USER" is assigned.
     *
     * @param token a JWT token
     * @return an Authentication object containing the user's credentials and authorities
     */
    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String username = claims.getSubject();
        String role = claims.get("role", String.class);
        String userIdStr = claims.get("userId", String.class);
        UUID userId = userIdStr != null ? UUID.fromString(userIdStr) : null;
        Integer tokenVersionObj = claims.get("version", Integer.class);
        int tokenVersion = tokenVersionObj != null ? tokenVersionObj : 1;

        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role != null ? role : "ROLE_USER");
        UserPrincipal userPrincipal = new UserPrincipal(userId, username, Collections.singletonList(authority), tokenVersion);

        return new UsernamePasswordAuthenticationToken(userPrincipal, token, userPrincipal.getAuthorities());
    }

    /**
     * Extracts the username from a JWT token.
     *
     * @param  token the JWT token
     * @return       the username encoded in the token
     */
    public String getUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Validates the integrity and structure of a JWT token.
     *
     * @param token the JWT token string to validate
     * @return {@code true} if the token is valid and properly signed, {@code false} otherwise
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.error("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.error("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.error("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.error("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    /**
     * Extracts the expiration time (in milliseconds) from a JWT token.
     */
    public long getExpirationTime(String token) {
        return parseClaims(token).getExpiration().getTime();
    }

    /**
     * Extracts the claims from a JWT token.
     *
     * @param token the JWT token to parse
     * @return the claims contained in the token
     */
    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }
}