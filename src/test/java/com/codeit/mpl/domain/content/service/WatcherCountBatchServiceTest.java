package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatcherCountBatchServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private WatcherCountSyncWorker syncWorker;

    @Mock
    private ObjectProvider<CacheManager> cacheManagerProvider;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private WatcherCountBatchService watcherCountBatchService;

    @Test
    @DisplayName("이전 배치 미처리 잔여 키가 존재하는 경우 unionAndStore를 호출한다")
    void syncWatcherCounts_whenProcessingKeyHasExistingSize() {
        // given
        String processingKey = "watcher-dirty-set:processing";
        String originKey = "watcher-dirty-set";

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.size(processingKey)).willReturn(5L);
        given(setOperations.members(processingKey)).willReturn(Set.of());

        // when
        watcherCountBatchService.syncWatcherCounts();

        // then
        verify(setOperations).unionAndStore(processingKey, originKey, processingKey);
        verify(redisTemplate).delete(originKey);
    }

    @Test
    @DisplayName("rename 시 예외가 발생하면(새로운 이벤트 없음) 조기 종료한다")
    void syncWatcherCounts_whenRenameFails_earlyReturn() {
        // given
        String processingKey = "watcher-dirty-set:processing";
        String originKey = "watcher-dirty-set";

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.size(processingKey)).willReturn(0L);
        doThrow(new RuntimeException("key not found")).when(redisTemplate).rename(originKey, processingKey);

        // when
        watcherCountBatchService.syncWatcherCounts();

        // then
        verify(setOperations, never()).members(any());
    }

    @Test
    @DisplayName("dirtyContentIds가 비어있으면 즉시 종료한다")
    void syncWatcherCounts_whenDirtyContentIdsEmpty_earlyReturn() {
        // given
        String processingKey = "watcher-dirty-set:processing";
        String originKey = "watcher-dirty-set";

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.size(processingKey)).willReturn(0L);
        given(setOperations.members(processingKey)).willReturn(Set.of());

        // when
        watcherCountBatchService.syncWatcherCounts();

        // then
        verify(cacheManagerProvider, never()).getIfAvailable();
    }

    @Test
    @DisplayName("정상 동기화 흐름: DB 동기화 성공 시 Lua 스크립트 실행 및 캐시를 갱신한다")
    void syncWatcherCounts_successFlow() {
        // given
        String processingKey = "watcher-dirty-set:processing";
        String originKey = "watcher-dirty-set";
        UUID contentId = UUID.randomUUID();
        String contentIdStr = contentId.toString();
        String deltaKey = "watcher-delta:" + contentIdStr;

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.size(processingKey)).willReturn(0L);
        given(setOperations.members(processingKey)).willReturn(Set.of(contentIdStr));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(deltaKey)).willReturn("3");

        given(cacheManagerProvider.getIfAvailable()).willReturn(cacheManager);
        given(cacheManager.getCache("content-detail")).willReturn(cache);
        given(cacheManager.getCache(com.codeit.mpl.infra.redis.RedisCacheConfig.CACHE_CONTENT_LIST)).willReturn(cache);

        ContentDto updatedDto = mock(ContentDto.class);
        given(syncWorker.syncDbOnly(contentId, 3L)).willReturn(updatedDto);

        // when
        watcherCountBatchService.syncWatcherCounts();

        // then
        verify(syncWorker).syncDbOnly(contentId, 3L);
        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq("3"));
        verify(cache).put(contentId, updatedDto);
        verify(cache).clear();
        verify(redisTemplate).delete(processingKey);
    }

    @Test
    @DisplayName("DB 동기화 예외 발생 시 originKey 세트에 requeue한다")
    void syncWatcherCounts_whenSyncWorkerFails_requeuesToOriginKey() {
        // given
        String processingKey = "watcher-dirty-set:processing";
        String originKey = "watcher-dirty-set";
        UUID contentId = UUID.randomUUID();
        String contentIdStr = contentId.toString();
        String deltaKey = "watcher-delta:" + contentIdStr;

        given(redisTemplate.opsForSet()).willReturn(setOperations);
        given(setOperations.size(processingKey)).willReturn(0L);
        given(setOperations.members(processingKey)).willReturn(Set.of(contentIdStr));
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get(deltaKey)).willReturn("2");

        given(cacheManagerProvider.getIfAvailable()).willReturn(null);
        doThrow(new RuntimeException("DB Connection Fail")).when(syncWorker).syncDbOnly(contentId, 2L);

        // when
        watcherCountBatchService.syncWatcherCounts();

        // then
        verify(setOperations).add(originKey, contentIdStr);
        verify(redisTemplate).delete(processingKey);
    }
}
