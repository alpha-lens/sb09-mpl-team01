package com.codeit.mpl.infra.security;

import com.codeit.mpl.domain.user.entity.User;
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
        String refreshToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + userId,
                refreshToken,
                refreshExpirationMs,
                TimeUnit.MILLISECONDS
        );
        return refreshToken;
    }

    public boolean validateRefreshToken(UUID userId, String token) {
        Object stored = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + userId);
        return stored != null && stored.toString().equals(token);
    }

    public void deleteRefreshToken(UUID userId) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
    }
}
