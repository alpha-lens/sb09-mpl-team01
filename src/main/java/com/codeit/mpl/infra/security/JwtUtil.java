package com.codeit.mpl.infra.security;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    public String generateAccessToken(User user) {
        return jwtTokenProvider.createToken(user.getEmail(), "ROLE_" + user.getRole().name());
    }

    public String generateRefreshToken(UUID userId) {
        String token = userId + ":" + UUID.randomUUID();
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                token,
                refreshExpirationMs,
                TimeUnit.MILLISECONDS
        );
        return token;
    }

    public UUID extractUserIdFromRefreshToken(String token) {
        if (token == null) throw new MplException(ErrorCode.INVALID_TOKEN);
        String[] parts = token.split(":", 2);
        if (parts.length != 2) throw new MplException(ErrorCode.INVALID_TOKEN);
        try {
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new MplException(ErrorCode.INVALID_TOKEN);
        }
    }

    public boolean validateRefreshToken(String token) {
        UUID userId = extractUserIdFromRefreshToken(token);
        Object stored = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
        return stored != null && stored.toString().equals(token);
    }

    public void deleteRefreshToken(UUID userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }
}
