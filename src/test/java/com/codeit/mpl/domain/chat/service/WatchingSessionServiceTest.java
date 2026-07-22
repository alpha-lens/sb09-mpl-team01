package com.codeit.mpl.domain.chat.service;

import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Field;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WatchingSessionServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ContentService contentService;
    @Mock
    private ContentRepository contentRepository;
    @Mock
    private BinaryContentStorage binaryContentStorage;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private WatchingSessionService watchingSessionService;

    private UUID userId;
    private UUID contentId;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        user = User.builder()
                .email("test@example.com")
                .name("Tester")
                .profileImageUrl("img.jpg")
                .build();

        Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);
    }

    @Test
    @DisplayName("touchSession - 사용자가 존재하지 않으면 동작하지 않음")
    void touchSession_userNotFound() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.empty());

        watchingSessionService.touchSession("test@example.com", contentId);

        verify(userRepository).findByEmail("test@example.com");
    }

    @Test
    @DisplayName("touchSession - 사용자가 존재하고 레디스 결과가 1L인 경우")
    void touchSession_success() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(1L);

        watchingSessionService.touchSession("test@example.com", contentId);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("touchSession - 레디스 결과가 0L인 경우")
    void touchSession_ignored() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));
        given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(0L);

        watchingSessionService.touchSession("test@example.com", contentId);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("removeSession - 세션 제거 스크립트 실행")
    void removeSession_success() {
        watchingSessionService.removeSession(userId);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(userId.toString())));
    }

    @Test
    @DisplayName("findWatchingSessionByWatcher - 레디스에 저장된 세션이 없을 때 null 반환")
    void findWatchingSessionByWatcher_null() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(null);

        WatchingSessionDto result = watchingSessionService.findWatchingSessionByWatcher(userId);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findWatchingSessionByWatcher - 세션 존재 시 WatchingSessionDto 반환")
    void findWatchingSessionByWatcher_found() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(contentId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(contentService.getContent(contentId)).willReturn(null);
        given(binaryContentStorage.getUrl("img.jpg")).willReturn("http://s3/img.jpg");

        WatchingSessionDto result = watchingSessionService.findWatchingSessionByWatcher(userId);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("registerSession - 존재하지 않는 콘텐츠의 경우 예외 발생")
    void registerSession_invalidContent() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(null);

        given(contentRepository.incrementWatcherCount(contentId)).willReturn(0);

        assertThatThrownBy(() -> watchingSessionService.registerSession(userId, contentId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("존재하지 않는 콘텐츠입니다");
    }

    @Test
    @DisplayName("registerSession - 기존 동일 세션 등록 시 시청 수 증가하지 않음")
    void registerSession_alreadyRegistered() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(contentId.toString());

        watchingSessionService.registerSession(userId, contentId);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    @DisplayName("getWatcherCount - 활성 시청자 수 반환")
    void getWatcherCount_success() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(5L);

        long count = watchingSessionService.getWatcherCount(contentId);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    @DisplayName("cleanExpiredSessions - 콘텐츠 키 존재 및 만료 세션 삭제")
    void cleanExpiredSessions_withKeys() {
        given(redisTemplate.keys("watching:content:*")).willReturn(Set.of("watching:content:123"));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).willReturn(2L);

        watchingSessionService.cleanExpiredSessions();

        verify(zSetOperations).removeRangeByScore(eq("watching:content:123"), eq(0.0), anyDouble());
    }

    @Test
    @DisplayName("cleanExpiredSessions - 콘텐츠 키 없음")
    void cleanExpiredSessions_emptyKeys() {
        given(redisTemplate.keys("watching:content:*")).willReturn(Collections.emptySet());

        watchingSessionService.cleanExpiredSessions();
    }

    @Test
    @DisplayName("findWatchingSessionsByContent - 검색어 없음, 결과 있음")
    void findWatchingSessionsByContent_noKeyword() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(Set.of(userId.toString()));
        given(userRepository.findAllById(anyList())).willReturn(List.of(user));
        given(contentService.getContent(contentId)).willReturn(null);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(1L);

        CursorPageRequestDto request = new CursorPageRequestDto(null, null, 10, Direction.DESCENDING, "createdAt");
        CursorPageResponseDto<WatchingSessionDto> response = watchingSessionService.findWatchingSessionsByContent(contentId, null, request);

        assertThat(response.data()).hasSize(1);
    }
}
