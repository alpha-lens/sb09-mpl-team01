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
        given(redisTemplate.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .willReturn(Set.of("watching:content:123"));
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble())).willReturn(2L);

        watchingSessionService.cleanExpiredSessions();

        verify(zSetOperations).removeRangeByScore(eq("watching:content:123"), eq(0.0), anyDouble());
    }

    @Test
    @DisplayName("cleanExpiredSessions - 콘텐츠 키 없음")
    void cleanExpiredSessions_emptyKeys() {
        given(redisTemplate.execute(any(org.springframework.data.redis.core.RedisCallback.class)))
                .willReturn(Collections.emptySet());

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

    @Test
    @DisplayName("findWatchingSessionsByContent - 검색어 없음, ZSET 조회 결과가 null일 때 empty response 반환")
    void findWatchingSessionsByContent_noKeyword_emptyZset() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(null);

        CursorPageRequestDto request = new CursorPageRequestDto(null, null, 10, Direction.DESCENDING, "createdAt");
        CursorPageResponseDto<WatchingSessionDto> response = watchingSessionService.findWatchingSessionsByContent(contentId, null, request);

        assertThat(response.data()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("findWatchingSessionsByContent - 검색어 없음, idAfter 커서 점수 및 hasNext=true 처리")
    void findWatchingSessionsByContent_noKeyword_withCursorAndHasNext() {
        UUID u1Id = UUID.randomUUID();
        UUID u2Id = UUID.randomUUID();
        UUID u3Id = UUID.randomUUID();

        User u1 = User.builder().name("U1").build();
        User u2 = User.builder().name("U2").build();
        User u3 = User.builder().name("U3").build();

        try {
            Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u1, u1Id);
            idField.set(u2, u2Id);
            idField.set(u3, u3Id);
        } catch (Exception ignored) {}

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.score(anyString(), eq(u1Id.toString()))).willReturn(10000.0);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), eq(9999.0), eq(0L), eq(3L)))
                .willReturn(new LinkedHashSet<>(List.of(u1Id.toString(), u2Id.toString(), u3Id.toString())));

        given(userRepository.findAllById(anyList())).willReturn(List.of(u1, u2, u3));
        given(contentService.getContent(contentId)).willReturn(null);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(3L);

        CursorPageRequestDto request = new CursorPageRequestDto(null, u1Id, 2, Direction.DESCENDING, "createdAt");
        CursorPageResponseDto<WatchingSessionDto> response = watchingSessionService.findWatchingSessionsByContent(contentId, null, request);

        assertThat(response.data()).hasSize(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextIdAfter()).isEqualTo(u2Id.toString());
    }

    @Test
    @DisplayName("findWatchingSessionsByContent - 검색어 포함, ZSET 조회 결과가 null일 때 empty response 반환")
    void findWatchingSessionsByContent_withKeyword_emptyZset() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), anyLong(), anyLong()))
                .willReturn(null);

        CursorPageRequestDto request = new CursorPageRequestDto(null, null, 10, Direction.DESCENDING, "createdAt");
        CursorPageResponseDto<WatchingSessionDto> response = watchingSessionService.findWatchingSessionsByContent(contentId, "Tester", request);

        assertThat(response.data()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("findWatchingSessionsByContent - 검색어 포함, DB 검색 후 idAfter 시점과 hasNext=true 페이징 검증")
    void findWatchingSessionsByContent_withKeyword_andPaging() {
        UUID u1Id = UUID.randomUUID();
        UUID u2Id = UUID.randomUUID();
        UUID u3Id = UUID.randomUUID();

        User u1 = User.builder().name("Tester 1").build();
        User u2 = User.builder().name("Tester 2").build();
        User u3 = User.builder().name("Tester 3").build();

        try {
            Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u1, u1Id);
            idField.set(u2, u2Id);
            idField.set(u3, u3Id);
        } catch (Exception ignored) {}

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(0L), eq(500L)))
                .willReturn(new LinkedHashSet<>(List.of(u1Id.toString(), u2Id.toString(), u3Id.toString())));

        given(userRepository.findByIdInAndNameContaining(anyList(), eq("Tester")))
                .willReturn(new ArrayList<>(List.of(u1, u2, u3)));
        given(contentService.getContent(contentId)).willReturn(null);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(3L);

        // idAfter가 u1Id이므로 findStartIndex는 u1Id 다음 인덱스(인덱스 1)부터 가져옴
        CursorPageRequestDto request = new CursorPageRequestDto(null, u1Id, 1, Direction.DESCENDING, "createdAt");
        CursorPageResponseDto<WatchingSessionDto> response = watchingSessionService.findWatchingSessionsByContent(contentId, "Tester", request);

        assertThat(response.data()).hasSize(1);
        assertThat(response.data().get(0).watcher().userId()).isEqualTo(u2Id);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextIdAfter()).isEqualTo(u2Id.toString());
    }

    @Test
    @DisplayName("findWatchingSessionsByContent - 검색어 포함, idAfter가 검색 결과에 존재하지 않는 경우 (findStartIndex fallback)")
    void findWatchingSessionsByContent_withKeyword_idAfterNotFound() {
        UUID u1Id = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        User u1 = User.builder().name("Tester 1").build();
        try {
            Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u1, u1Id);
        } catch (Exception ignored) {}

        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble(), eq(0L), eq(500L)))
                .willReturn(new LinkedHashSet<>(List.of(u1Id.toString())));
        given(userRepository.findByIdInAndNameContaining(anyList(), eq("Tester")))
                .willReturn(new ArrayList<>(List.of(u1)));
        given(contentService.getContent(contentId)).willReturn(null);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(1L);

        CursorPageRequestDto request = new CursorPageRequestDto(null, unknownId, 10, Direction.DESCENDING, "createdAt");
        CursorPageResponseDto<WatchingSessionDto> response = watchingSessionService.findWatchingSessionsByContent(contentId, "Tester", request);

        assertThat(response.data()).hasSize(1);
    }

    @Test
    @DisplayName("registerSession - 이전 다른 콘텐츠 세션이 존재하는 경우 신규 시청수 증가")
    void registerSession_withPreviousContent() {
        UUID prevContentId = UUID.randomUUID();
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(prevContentId.toString());

        given(contentRepository.incrementWatcherCount(contentId)).willReturn(1);
        given(redisTemplate.execute(any(RedisScript.class), anyList(), anyString())).willReturn(1L);

        watchingSessionService.registerSession(userId, contentId);

        verify(contentRepository).incrementWatcherCount(contentId);
    }

    @Test
    @DisplayName("findWatchingSessionByWatcher - 프로필 이미지가 없는 경우 처리")
    void findWatchingSessionByWatcher_nullProfileImage() {
        User userNoImg = User.builder().email("noimg@test.com").name("NoImg").profileImageUrl(null).build();
        try {
            Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(userNoImg, userId);
        } catch (Exception ignored) {}

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(contentId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.of(userNoImg));
        given(contentService.getContent(contentId)).willReturn(null);

        WatchingSessionDto dto = watchingSessionService.findWatchingSessionByWatcher(userId);

        assertThat(dto).isNotNull();
        assertThat(dto.watcher().profileImageUrl()).isNull();
    }

    @Test
    @DisplayName("findWatchingSessionByWatcher - 유저가 DB에 존재하지 않으면 null 반환")
    void findWatchingSessionByWatcher_userNotFoundInDb() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.get("watching:user:" + userId)).willReturn(contentId.toString());
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        WatchingSessionDto dto = watchingSessionService.findWatchingSessionByWatcher(userId);

        assertThat(dto).isNull();
    }

    @Test
    @DisplayName("getWatcherCount - 레디스가 null을 반환할 때 0L 기본값 처리")
    void getWatcherCount_nullReturn() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.count(anyString(), anyDouble(), anyDouble())).willReturn(null);

        long count = watchingSessionService.getWatcherCount(contentId);

        assertThat(count).isEqualTo(0L);
    }

    @Test
    @DisplayName("getActiveWatcherSnapshot - 활성 시청자 스냅샷 반환")
    void getActiveWatcherSnapshot_success() {
        given(redisTemplate.opsForZSet()).willReturn(zSetOperations);
        given(zSetOperations.reverseRangeByScore(anyString(), anyDouble(), anyDouble()))
                .willReturn(Set.of(userId.toString()));
        given(userRepository.findAllById(anyList())).willReturn(List.of(user));
        given(contentService.getContent(contentId)).willReturn(null);

        var snapshot = watchingSessionService.getActiveWatcherSnapshot(contentId);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.totalCount()).isEqualTo(1L);
        assertThat(snapshot.watchers()).hasSize(1);
    }
}
