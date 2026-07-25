package com.codeit.mpl.infra.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 기반 캐시 설정.
 *
 * <ul>
 *   <li>content-detail : TTL 1시간 — 콘텐츠 메타데이터 + 저장된 집계 통계(averageRating·reviewCount·watcherCount).
 *       리뷰 변경 시 즉시 evict, watcherCount 임계값 도달 시 즉시 evict.</li>
 *   <li>content-list   : TTL 5분 — 키워드 없는 목록 조회 결과(cursor/idAfter 포함 키, 최대 3페이지).</li>
 * </ul>
 *
 * {@link CachingConfigurer}를 구현해 Redis 장애 시 예외를 삼키고 원본 메서드(DB 조회)로 폴백합니다.
 * Elasticsearch 장애 시 DB로 폴백하는 기존 패턴과 동일한 방어 전략입니다.
 */
@Slf4j
@Configuration
@EnableCaching
@Profile("!test")
public class RedisCacheConfig implements CachingConfigurer {

    public static final String CACHE_CONTENT_DETAIL = "content-detail";
    public static final String CACHE_CONTENT_LIST   = "content-list";

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory, ObjectMapper springObjectMapper) {
        // Spring Boot의 ObjectMapper를 기반으로, Java Record(final 클래스)도 
        // 타입 정보(@class)를 포함하도록 DefaultTyping.EVERYTHING 설정
        ObjectMapper objectMapper = springObjectMapper.copy()
                .activateDefaultTyping(
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType(Object.class)
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY
                );

        RedisSerializer<Object> jsonSerializer = new RedisSerializer<Object>() {
            @Override
            public byte[] serialize(Object t) throws SerializationException {
                if (t == null) return new byte[0];
                try {
                    return objectMapper.writeValueAsBytes(t);
                } catch (Exception e) {
                    throw new SerializationException("Serialization error", e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return objectMapper.readValue(bytes, Object.class);
                } catch (Exception e) {
                    throw new SerializationException("Deserialization error", e);
                }
            }
        };

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        // 1시간: 콘텐츠 내용은 잘 안 바뀌고, 리뷰·watcherCount 변경 시 즉시 evict
        cacheConfigs.put(CACHE_CONTENT_DETAIL, base.entryTtl(Duration.ofHours(1)));
        // 5분: watcherCount·rating 순위 변동 주기에 맞게 짧게 유지
        cacheConfigs.put(CACHE_CONTENT_LIST,   base.entryTtl(Duration.ofMinutes(5)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    /**
     * Redis 장애 시 캐시 오류를 경고 로그로만 남기고 원본 메서드(DB 조회)로 폴백합니다.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] GET 실패 — DB 폴백 실행: cache={}, key={}: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] PUT 실패 — cache={}, key={}: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] EVICT 실패 — cache={}, key={}: {}",
                        cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] CLEAR 실패 — cache={}: {}",
                        cache.getName(), e.getMessage());
            }
        };
    }
}
