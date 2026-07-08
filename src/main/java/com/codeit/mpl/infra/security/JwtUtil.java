package com.codeit.mpl.infra.security;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpirationMs;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_ACCESS_TOKEN_PREFIX = "blacklist:access_token:";
    private static final String TOKEN_VERSION_PREFIX = "user:token_version:";
    private static final long TOKEN_VERSION_CACHE_TTL_SECONDS = 4200;

    public String generateAccessToken(User user) {
        return jwtTokenProvider.createToken(
                user.getEmail(), "ROLE_" + user.getRole().name(), user.getId().toString(), user.getTokenVersion());
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
        try {
            redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Failed to delete refresh token: {}", e.getMessage());
        }
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public void blacklistAccessToken(String token, long expirationTimeMs) {
        long remainingTimeMs = expirationTimeMs - System.currentTimeMillis();
        if (remainingTimeMs > 0) {
            String key = BLACKLIST_ACCESS_TOKEN_PREFIX + hashToken(token);
            redisTemplate.opsForValue().set(key, "true", remainingTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    public boolean isAccessTokenBlacklisted(String token) {
        try {
            String key = BLACKLIST_ACCESS_TOKEN_PREFIX + hashToken(token);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.warn("Fail-Open: Redis error occurred during blacklist check: {}", e.getMessage());
            return false;
        }
    }

    public int getCurrentTokenVersion(UUID userId) {
        String key = TOKEN_VERSION_PREFIX + userId;
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return Integer.parseInt(cached.toString());
            }
            Integer dbVersion = userRepository.findTokenVersionById(userId);
            if (dbVersion == null) {
                // Redis/DB 인프라 장애가 아니라 "그런 사용자가 없다"는 정상 응답이므로,
                // 삭제된 사용자의 토큰이 fail-open 기본값을 타고 통과하지 않도록 여기서만 fail-closed로 막는다.
                log.warn("Rejecting token version check: user not found: {}", userId);
                return Integer.MAX_VALUE;
            }
            redisTemplate.opsForValue().set(key, dbVersion, TOKEN_VERSION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            return dbVersion;
        } catch (Exception e) {
            log.error("Fail-Open: Redis error occurred during token version check (falling back to DB): {}", e.getMessage());
            try {
                Integer dbVersion = userRepository.findTokenVersionById(userId);
                return dbVersion != null ? dbVersion : 1;
            } catch (Exception dbEx) {
                log.error("Fallback DB lookup failed: {}", dbEx.getMessage());
                return 1;
            }
        }
    }

    public void updateTokenVersionInRedis(UUID userId, int newVersion) {
        String key = TOKEN_VERSION_PREFIX + userId;
        try {
            redisTemplate.opsForValue().set(key, newVersion, TOKEN_VERSION_CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to update token version in Redis, evicting stale cache instead: {}", e.getMessage());
            try {
                // set이 실패했다고 옛 버전 캐시를 TTL 끝까지 그대로 두면, 이미 무효화됐어야 할 토큰이
                // 그 기간 동안 계속 통과할 수 있다. 최소한 캐시를 지워서 다음 조회가 DB(최신 값)로 폴백하게 한다.
                redisTemplate.delete(key);
            } catch (Exception evictEx) {
                log.error("Failed to evict stale token version cache: {}", evictEx.getMessage());
            }
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }
}
