package com.codeit.mpl.domain.chat.service;

import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    void setUp() {
        userId = UUID.randomUUID();
        contentId = UUID.randomUUID();
        user = User.builder()
                .email("test@example.com")
                .name("Tester")
                .build();
    }

    @Test
    @DisplayName("touchSession - 사용자가 존재하지 않으면 동작하지 않음")
    void touchSession_userNotFound() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.empty());

        watchingSessionService.touchSession("test@example.com", contentId);

        verify(userRepository).findByEmail("test@example.com");
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
    @DisplayName("getWatcherCount - 활성 시청자 수 반환")
    void getWatcherCount_success() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(5L);

        long count = watchingSessionService.getWatcherCount(contentId);

        assertThat(count).isEqualTo(5L);
    }
}
