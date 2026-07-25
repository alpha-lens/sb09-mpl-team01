package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatcherCountBatchService {
    private final StringRedisTemplate redisTemplate;
    private final WatcherCountSyncWorker syncWorker;
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    // DECRBY 후 값이 0 이하가 되면 키 삭제까지 한 번에 원자적으로 처리
    private static final RedisScript<Long> DECR_AND_CLEAN_SCRIPT = RedisScript.of("""
        local remaining = redis.call('DECRBY', KEYS[1], ARGV[1])
        if remaining <= 0 then
            redis.call('DEL', KEYS[1])
        end
        return remaining
        """, Long.class);

    @Scheduled(fixedDelay = 60000) // 1분 주기
    public void syncWatcherCounts() {
        String processingKey = "watcher-dirty-set:processing";
        String originKey = "watcher-dirty-set";

        // 1. 이전 배치 실행이 실패해서 남겨진 잔여물이 있는지 확인하고, 있으면 UNION으로 합치기
        Long existingSize = redisTemplate.opsForSet().size(processingKey);
        if (existingSize != null && existingSize > 0) {
            redisTemplate.opsForSet().unionAndStore(processingKey, originKey, processingKey);
            redisTemplate.delete(originKey);
        } else {
            try {
                redisTemplate.rename(originKey, processingKey);
            } catch (Exception e) {
                return; // 원본 키가 없으면 (새로운 이벤트가 없으면) 종료
            }
        }

        Set<String> dirtyContentIds = redisTemplate.opsForSet().members(processingKey);
        if (dirtyContentIds == null || dirtyContentIds.isEmpty()) return;

        CacheManager cacheManager = cacheManagerProvider.getIfAvailable();
        Cache cache = cacheManager != null ? cacheManager.getCache("content-detail") : null;

        for (String contentIdStr : dirtyContentIds) {
            String deltaKey = "watcher-delta:" + contentIdStr;
            String deltaStr = redisTemplate.opsForValue().get(deltaKey);
            if (deltaStr == null) continue;

            long delta = Long.parseLong(deltaStr);
            if (delta == 0) continue;

            try {
                UUID contentId = UUID.fromString(contentIdStr);

                // 1. DB 반영 트랜잭션 실행 (이 메서드가 예외 없이 반환되면 커밋 성공을 의미)
                ContentDto updatedDto = syncWorker.syncDbOnly(contentId, delta);

                // 2. 커밋 완료 후, DECRBY + 조건부 DEL을 Lua 스크립트로 원자적 처리
                //    DECRBY와 DEL 사이에 다른 INCR가 끼어들 여지가 없어짐
                redisTemplate.execute(
                    DECR_AND_CLEAN_SCRIPT,
                    Collections.singletonList(deltaKey),
                    String.valueOf(delta)
                );

                if (cache != null) {
                    cache.put(contentId, updatedDto);
                }

            } catch (Exception e) {
                log.error("sync failed for {}, delta will be requeued", contentIdStr, e);
                // DB 롤백 발생, Redis 델타는 아직 손대지 않았으므로 안전하게 보존됨
                // 다음 배치 사이클에서 다시 처리될 수 있도록 원본 큐(Set)에 복구
                redisTemplate.opsForSet().add(originKey, contentIdStr);
            }
        }

        // 3. 델타 동기화가 완료되었으므로 목록 캐시(content-list)도 함께 무효화하여 1분 주기로 목록 조회수가 갱신되도록 함
        Cache listCache = cacheManager != null ? cacheManager.getCache(com.codeit.mpl.infra.redis.RedisCacheConfig.CACHE_CONTENT_LIST) : null;
        if (listCache != null) {
            listCache.clear();
        }

        // 처리 완료 후 processing 키 삭제
        redisTemplate.delete(processingKey);
    }
}
