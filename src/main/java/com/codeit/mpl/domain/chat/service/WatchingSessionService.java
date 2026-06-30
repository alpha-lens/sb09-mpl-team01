package com.codeit.mpl.domain.chat.service;

import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingSessionService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    // Lua 스크립트 리소스 정의 (Spring DefaultRedisScript는 내부적으로 SHA 캐싱을 처리함)
    private final RedisScript<Long> registerScript = RedisScript.of(new ClassPathResource("scripts/register_session.lua"), Long.class);
    private final RedisScript<Long> touchScript = RedisScript.of(new ClassPathResource("scripts/touch_session.lua"), Long.class);
    private final RedisScript<Long> removeScript = RedisScript.of(new ClassPathResource("scripts/remove_session.lua"), Long.class);

    private static final String USER_KEY_PREFIX = "watching:user:";
    private static final String CONTENT_KEY_PREFIX = "watching:content:";

    public void touchSession(String email, UUID contentId) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String watcherId = user.getId().toString();
            String contentKey = CONTENT_KEY_PREFIX + contentId.toString();
            String now = String.valueOf(Instant.now().toEpochMilli());

            Long result = redisTemplate.execute(
                touchScript,
                List.of(watcherId, contentId.toString()),
                now
            );

            if (Long.valueOf(1).equals(result)) {
                log.debug("[WatchingSession] Touch session success for user={}, content={}", watcherId, contentId);
            } else {
                log.debug("[WatchingSession] Touch session ignored (no active session found) for user={}, content={}", watcherId, contentId);
            }
        });
    }

    public WatchingSessionDto findWatchingSessionByWatcher(UUID watcherId) {
        String contentIdStr = redisTemplate.opsForValue().get(USER_KEY_PREFIX + watcherId.toString());
        if (contentIdStr == null) {
            return null;
        }

        return userRepository.findById(watcherId)
            .map(user -> new WatchingSessionDto(
                UUID.fromString(contentIdStr),
                new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl())
            ))
            .orElse(null);
    }

    public CursorPageResponseDto<WatchingSessionDto> findWatchingSessionsByContent(
        UUID contentId, String watcherNameLike, CursorPageRequestDto request
    ) {
        int limit = request.limit() != null ? request.limit() : 20;
        UUID idAfter = request.idAfter();
        String contentKey = CONTENT_KEY_PREFIX + contentId.toString();

        double minScore = Instant.now().minusSeconds(300).toEpochMilli();
        List<WatchingSessionDto> dtos;
        boolean hasNext;

        if (watcherNameLike == null || watcherNameLike.isBlank()) {
            // [경로 A] 이름 검색 조건이 없을 때: Redis ZSET에서 직접 범위 페이징 수행
            Double score = null;
            if (idAfter != null) {
                score = redisTemplate.opsForZSet().score(contentKey, idAfter.toString());
            }

            double maxScore = score != null ? score - 1 : Double.MAX_VALUE;
            Set<String> watcherIds = redisTemplate.opsForZSet().reverseRangeByScore(
                contentKey, minScore, maxScore, 0, limit + 1
            );

            if (watcherIds == null || watcherIds.isEmpty()) {
                return new CursorPageResponseDto<>(List.of(), null, null, false, 0, "createdAt", Direction.DESCENDING);
            }

            List<UUID> uuids = watcherIds.stream().map(UUID::fromString).toList();
            Map<UUID, User> userMap = userRepository.findAllById(uuids).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

            List<WatchingSessionDto> unsortedDtos = uuids.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(user -> new WatchingSessionDto(
                    contentId,
                    new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl())
                ))
                .toList();

            hasNext = unsortedDtos.size() > limit;
            dtos = hasNext ? unsortedDtos.subList(0, limit) : unsortedDtos;

        } else {
            // [경로 B] 이름 검색 조건이 있을 때: 최근 활성 유저 최대 500명 한정 DB 레벨 필터링 및 메모리 페이징
            Set<String> allWatcherIds = redisTemplate.opsForZSet().reverseRangeByScore(
                contentKey, minScore, Double.MAX_VALUE, 0, 500
            );

            if (allWatcherIds == null || allWatcherIds.isEmpty()) {
                return new CursorPageResponseDto<>(List.of(), null, null, false, 0, "createdAt", Direction.DESCENDING);
            }

            List<UUID> sortedIds = allWatcherIds.stream().map(UUID::fromString).toList();
            Map<UUID, Integer> indexMap = new HashMap<>();
            for (int i = 0; i < sortedIds.size(); i++) {
                indexMap.put(sortedIds.get(i), i);
            }

            List<User> filteredUsers = userRepository.findByIdInAndNameContaining(sortedIds, watcherNameLike);
            // ZSET의 활성 시간 역순으로 정렬 보존
            filteredUsers.sort(Comparator.comparingInt(u -> indexMap.getOrDefault(u.getId(), Integer.MAX_VALUE)));

            int startIndex = 0;
            if (idAfter != null) {
                for (int i = 0; i < filteredUsers.size(); i++) {
                    if (filteredUsers.get(i).getId().equals(idAfter)) {
                        startIndex = i + 1;
                        break;
                    }
                }
            }

            List<User> pageUsers = filteredUsers.subList(startIndex, Math.min(startIndex + limit + 1, filteredUsers.size()));
            hasNext = pageUsers.size() > limit;
            List<User> finalUsers = hasNext ? pageUsers.subList(0, limit) : pageUsers;

            dtos = finalUsers.stream()
                .map(user -> new WatchingSessionDto(
                    contentId,
                    new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl())
                ))
                .toList();
        }

        String nextCursor = null;
        String nextIdAfter = null;
        if (!dtos.isEmpty()) {
            WatchingSessionDto last = dtos.get(dtos.size() - 1);
            nextCursor = last.user().userId().toString();
            nextIdAfter = last.user().userId().toString();
        }

        Long totalCount = redisTemplate.opsForZSet().count(contentKey, minScore, Double.MAX_VALUE);
        long count = totalCount != null ? totalCount : 0L;

        return new CursorPageResponseDto<>(
            dtos,
            nextCursor,
            nextIdAfter,
            hasNext,
            count,
            "createdAt",
            Direction.DESCENDING
        );
    }

    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void cleanExpiredSessions() {
        Instant threshold = Instant.now().minusSeconds(300); // 5분 전
        double maxScore = threshold.toEpochMilli();

        log.debug("[WatchingSession Scheduler] Cleaning ZSET sessions inactive since {}", threshold);
        
        // Redis ZSET에서 5분 경과한 비활성 멤버 제거 (패턴 스캔 필요 없이 개별 Sorted Set에 대해 청소 수행)
        Set<String> keys = redisTemplate.keys(CONTENT_KEY_PREFIX + "*");
        if (keys != null) {
            for (String key : keys) {
                Long removed = redisTemplate.opsForZSet().removeRangeByScore(key, 0, maxScore);
                if (removed != null && removed > 0) {
                    log.info("[WatchingSession Scheduler] Cleaned {} expired sessions from key {}", removed, key);
                }
            }
        }
    }

    public void registerSession(UUID watcherId, UUID contentId) {
        String now = String.valueOf(Instant.now().toEpochMilli());
        redisTemplate.execute(
            registerScript,
            List.of(watcherId.toString(), contentId.toString()),
            now
        );
        log.info("[WatchingSession] Registered session for watcher={}, content={}", watcherId, contentId);
    }

    public void removeSession(UUID watcherId) {
        redisTemplate.execute(
            removeScript,
            List.of(watcherId.toString())
        );
        log.info("[WatchingSession] Removed session for watcher={}", watcherId);
    }

    public long getWatcherCount(UUID contentId) {
        String contentKey = CONTENT_KEY_PREFIX + contentId.toString();
        double minScore = Instant.now().minusSeconds(300).toEpochMilli();
        Long totalCount = redisTemplate.opsForZSet().count(contentKey, minScore, Double.MAX_VALUE);
        return totalCount != null ? totalCount : 0L;
    }
}
