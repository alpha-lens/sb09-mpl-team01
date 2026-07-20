package com.codeit.mpl.domain.chat.service;

import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchingSessionService {

    private static final String USER_KEY_PREFIX =
            "watching:user:";

    private static final String CONTENT_KEY_PREFIX =
            "watching:content:";

    private static final long SESSION_TIMEOUT_SECONDS =
            300L;

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ContentService contentService;
    private final ContentRepository contentRepository;
    private final BinaryContentStorage binaryContentStorage;

    /*
     * Spring의 RedisScript는 실행 시 Lua 스크립트 SHA를 캐싱합니다.
     */
    private final RedisScript<Long> registerScript =
            RedisScript.of(
                    new ClassPathResource(
                            "scripts/register_session.lua"
                    ),
                    Long.class
            );

    private final RedisScript<Long> touchScript =
            RedisScript.of(
                    new ClassPathResource(
                            "scripts/touch_session.lua"
                    ),
                    Long.class
            );

    private final RedisScript<Long> removeScript =
            RedisScript.of(
                    new ClassPathResource(
                            "scripts/remove_session.lua"
                    ),
                    Long.class
            );

    /**
     * 현재 시청 세션의 마지막 활성 시간을 갱신합니다.
     *
     * heartbeat에서는 누적 시청 횟수를 증가시키지 않습니다.
     */
    public void touchSession(
            String email,
            UUID contentId
    ) {
        userRepository.findByEmail(email)
                .ifPresent(user -> {
                    String watcherId =
                            user.getId()
                                    .toString();

                    String now =
                            String.valueOf(
                                    Instant.now()
                                            .toEpochMilli()
                            );

                    Long result =
                            redisTemplate.execute(
                                    touchScript,
                                    List.of(
                                            watcherId,
                                            contentId.toString()
                                    ),
                                    now
                            );

                    if (Long.valueOf(1L)
                            .equals(result)) {

                        log.debug(
                                "[WatchingSession] Touch session success: watcher={}, content={}",
                                watcherId,
                                contentId
                        );

                    } else {
                        log.debug(
                                "[WatchingSession] Touch ignored because active session was not found: watcher={}, content={}",
                                watcherId,
                                contentId
                        );
                    }
                });
    }

    /**
     * 특정 사용자가 현재 보고 있는 콘텐츠의 시청 세션을 조회합니다.
     */
    public WatchingSessionDto findWatchingSessionByWatcher(
            UUID watcherId
    ) {
        log.debug(
                "[WatchingSessionService] findWatchingSessionByWatcher: watcherId={}",
                watcherId
        );

        String contentIdString =
                redisTemplate.opsForValue()
                        .get(
                                USER_KEY_PREFIX
                                        + watcherId
                        );

        if (contentIdString == null) {
            log.debug(
                    "[WatchingSessionService] active session not found: watcherId={}",
                    watcherId
            );

            return null;
        }

        UUID contentId =
                UUID.fromString(
                        contentIdString
                );

        return userRepository.findById(watcherId)
                .map(user -> {
                    ContentDto contentDto =
                            contentService.getContent(
                                    contentId
                            );

                    String resolvedImageUrl =
                            user.getProfileImageUrl() != null
                                    ? binaryContentStorage.getUrl(
                                    user.getProfileImageUrl()
                            )
                                    : null;

                    return new WatchingSessionDto(
                            user.getId(),
                            Instant.now(),
                            new UserSummary(
                                    user.getId(),
                                    user.getName(),
                                    resolvedImageUrl
                            ),
                            contentDto
                    );
                })
                .orElse(null);
    }

    /**
     * 특정 콘텐츠를 현재 시청 중인 사용자 목록을 조회합니다.
     */
    public CursorPageResponseDto<WatchingSessionDto>
    findWatchingSessionsByContent(
            UUID contentId,
            String watcherNameLike,
            CursorPageRequestDto request
    ) {
        log.debug(
                "[WatchingSessionService] findWatchingSessionsByContent: contentId={}",
                contentId
        );

        int limit =
                request.limit() != null
                        ? request.limit()
                        : 20;

        UUID idAfter =
                request.idAfter();

        String contentKey =
                CONTENT_KEY_PREFIX
                        + contentId;

        double minScore =
                Instant.now()
                        .minusSeconds(
                                SESSION_TIMEOUT_SECONDS
                        )
                        .toEpochMilli();

        List<WatchingSessionDto> dtos;
        boolean hasNext;

        if (watcherNameLike == null
                || watcherNameLike.isBlank()) {

            /*
             * 이름 검색 조건이 없을 때:
             * Redis ZSET에서 활성 시간 역순으로 직접 페이지 조회합니다.
             */
            Double cursorScore = null;

            if (idAfter != null) {
                cursorScore =
                        redisTemplate.opsForZSet()
                                .score(
                                        contentKey,
                                        idAfter.toString()
                                );
            }

            double maxScore =
                    cursorScore != null
                            ? cursorScore - 1
                            : Double.MAX_VALUE;

            Set<String> watcherIds =
                    redisTemplate.opsForZSet()
                            .reverseRangeByScore(
                                    contentKey,
                                    minScore,
                                    maxScore,
                                    0,
                                    limit + 1L
                            );

            if (watcherIds == null
                    || watcherIds.isEmpty()) {

                return createEmptyResponse();
            }

            List<UUID> watcherUuids =
                    watcherIds.stream()
                            .map(UUID::fromString)
                            .toList();

            Map<UUID, User> userMap =
                    userRepository.findAllById(
                                    watcherUuids
                            )
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            User::getId,
                                            user -> user
                                    )
                            );

            ContentDto contentDto =
                    contentService.getContent(
                            contentId
                    );

            List<WatchingSessionDto> sessionDtos =
                    watcherUuids.stream()
                            .map(userMap::get)
                            .filter(Objects::nonNull)
                            .map(user ->
                                    createWatchingSessionDto(
                                            user,
                                            contentDto,
                                            contentKey
                                    )
                            )
                            .toList();

            hasNext =
                    sessionDtos.size()
                            > limit;

            dtos =
                    hasNext
                            ? sessionDtos.subList(
                            0,
                            limit
                    )
                            : sessionDtos;

        } else {
            /*
             * 이름 검색 조건이 있을 때:
             * 최근 활성 사용자 최대 500명을 기준으로 DB 이름 검색 후
             * Redis ZSET 순서를 유지합니다.
             */
            Set<String> allWatcherIds =
                    redisTemplate.opsForZSet()
                            .reverseRangeByScore(
                                    contentKey,
                                    minScore,
                                    Double.MAX_VALUE,
                                    0,
                                    500
                            );

            if (allWatcherIds == null
                    || allWatcherIds.isEmpty()) {

                return createEmptyResponse();
            }

            List<UUID> sortedWatcherIds =
                    allWatcherIds.stream()
                            .map(UUID::fromString)
                            .toList();

            Map<UUID, Integer> indexMap =
                    new HashMap<>();

            for (int index = 0;
                 index < sortedWatcherIds.size();
                 index++) {

                indexMap.put(
                        sortedWatcherIds.get(index),
                        index
                );
            }

            List<User> filteredUsers =
                    userRepository
                            .findByIdInAndNameContaining(
                                    sortedWatcherIds,
                                    watcherNameLike
                            );

            filteredUsers.sort(
                    Comparator.comparingInt(
                            user ->
                                    indexMap.getOrDefault(
                                            user.getId(),
                                            Integer.MAX_VALUE
                                    )
                    )
            );

            int startIndex =
                    findStartIndex(
                            filteredUsers,
                            idAfter
                    );

            int endIndex =
                    Math.min(
                            startIndex
                                    + limit
                                    + 1,
                            filteredUsers.size()
                    );

            List<User> pageUsers =
                    filteredUsers.subList(
                            startIndex,
                            endIndex
                    );

            hasNext =
                    pageUsers.size()
                            > limit;

            List<User> resultUsers =
                    hasNext
                            ? pageUsers.subList(
                            0,
                            limit
                    )
                            : pageUsers;

            ContentDto contentDto =
                    contentService.getContent(
                            contentId
                    );

            dtos =
                    resultUsers.stream()
                            .map(user ->
                                    createWatchingSessionDto(
                                            user,
                                            contentDto,
                                            contentKey
                                    )
                            )
                            .toList();
        }

        String nextCursor = null;
        String nextIdAfter = null;

        if (!dtos.isEmpty()) {
            WatchingSessionDto lastSession =
                    dtos.get(
                            dtos.size() - 1
                    );

            String lastWatcherId =
                    lastSession.watcher()
                            .userId()
                            .toString();

            nextCursor =
                    lastWatcherId;

            nextIdAfter =
                    lastWatcherId;
        }

        Long activeWatcherCount =
                redisTemplate.opsForZSet()
                        .count(
                                contentKey,
                                minScore,
                                Double.MAX_VALUE
                        );

        long totalCount =
                activeWatcherCount != null
                        ? activeWatcherCount
                        : 0L;

        return new CursorPageResponseDto<>(
                dtos,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                "createdAt",
                Direction.DESCENDING
        );
    }

    /**
     * Redis에서 5분 이상 heartbeat가 없는 세션을 제거합니다.
     *
     * watcherCount는 누적 시청 횟수이므로 여기서 변경하지 않습니다.
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanExpiredSessions() {
        Instant threshold =
                Instant.now()
                        .minusSeconds(
                                SESSION_TIMEOUT_SECONDS
                        );

        double maxScore =
                threshold.toEpochMilli();

        log.debug(
                "[WatchingSession Scheduler] Cleaning sessions inactive since {}",
                threshold
        );

        Set<String> contentKeys =
                redisTemplate.keys(
                        CONTENT_KEY_PREFIX
                                + "*"
                );

        if (contentKeys == null
                || contentKeys.isEmpty()) {

            return;
        }

        for (String contentKey : contentKeys) {
            Long removedCount =
                    redisTemplate.opsForZSet()
                            .removeRangeByScore(
                                    contentKey,
                                    0,
                                    maxScore
                            );

            if (removedCount != null
                    && removedCount > 0) {

                log.info(
                        "[WatchingSession Scheduler] Cleaned {} expired sessions from {}",
                        removedCount,
                        contentKey
                );
            }
        }
    }

    /**
     * 사용자의 콘텐츠 시청 세션을 등록합니다.
     *
     * 새로운 입장인 경우에만 콘텐츠의 누적 시청 횟수를 증가시킵니다.
     *
     * 동일 사용자가 같은 콘텐츠에 대해 register 요청을 반복하더라도
     * 누적 시청 횟수는 중복 증가하지 않습니다.
     */
    @Transactional
    public void registerSession(
            UUID watcherId,
            UUID contentId
    ) {
        String userKey =
                USER_KEY_PREFIX
                        + watcherId;

        String previousContentId =
                redisTemplate.opsForValue()
                        .get(userKey);

        boolean isNewView =
                previousContentId == null
                        || !previousContentId.equals(
                        contentId.toString()
                );

        String now =
                String.valueOf(
                        Instant.now()
                                .toEpochMilli()
                );

        redisTemplate.execute(
                registerScript,
                List.of(
                        watcherId.toString(),
                        contentId.toString()
                ),
                now
        );

        if (isNewView) {
            int updatedRows =
                    contentRepository
                            .incrementWatcherCount(
                                    contentId
                            );

            if (updatedRows == 0) {
                /*
                 * 존재하지 않는 콘텐츠가 Redis에 세션으로 남는 것을 막습니다.
                 */
                redisTemplate.execute(
                        removeScript,
                        List.of(
                                watcherId.toString()
                        )
                );

                throw new IllegalArgumentException(
                        "존재하지 않는 콘텐츠입니다: "
                                + contentId
                );
            }

            log.info(
                    "[WatchingSession] Increased cumulative watcher count: watcher={}, content={}",
                    watcherId,
                    contentId
            );

        } else {
            log.debug(
                    "[WatchingSession] Skipped cumulative count because the same session was already registered: watcher={}, content={}",
                    watcherId,
                    contentId
            );
        }

        log.info(
                "[WatchingSession] Registered session: watcher={}, content={}",
                watcherId,
                contentId
        );
    }

    /**
     * 현재 시청 세션을 제거합니다.
     *
     * watcherCount는 누적값이므로 감소시키지 않습니다.
     */
    public void removeSession(
            UUID watcherId
    ) {
        redisTemplate.execute(
                removeScript,
                List.of(
                        watcherId.toString()
                )
        );

        log.info(
                "[WatchingSession] Removed session: watcher={}",
                watcherId
        );
    }

    /**
     * 현재 활성 시청자 수를 반환합니다.
     *
     * 이 값은 Content.watcherCount의 누적값과 다른 값입니다.
     */
    public long getWatcherCount(
            UUID contentId
    ) {
        String contentKey =
                CONTENT_KEY_PREFIX
                        + contentId;

        double minScore =
                Instant.now()
                        .minusSeconds(
                                SESSION_TIMEOUT_SECONDS
                        )
                        .toEpochMilli();

        Long activeWatcherCount =
                redisTemplate.opsForZSet()
                        .count(
                                contentKey,
                                minScore,
                                Double.MAX_VALUE
                        );

        return activeWatcherCount != null
                ? activeWatcherCount
                : 0L;
    }

    private WatchingSessionDto createWatchingSessionDto(
            User user,
            ContentDto contentDto,
            String contentKey
    ) {
        Double userScore =
                redisTemplate.opsForZSet()
                        .score(
                                contentKey,
                                user.getId()
                                        .toString()
                        );

        Instant createdAt =
                userScore != null
                        ? Instant.ofEpochMilli(
                        userScore.longValue()
                )
                        : Instant.now();

        String resolvedImageUrl =
                user.getProfileImageUrl() != null
                        ? binaryContentStorage.getUrl(
                        user.getProfileImageUrl()
                )
                        : null;

        return new WatchingSessionDto(
                user.getId(),
                createdAt,
                new UserSummary(
                        user.getId(),
                        user.getName(),
                        resolvedImageUrl
                ),
                contentDto
        );
    }

    private int findStartIndex(
            List<User> users,
            UUID idAfter
    ) {
        if (idAfter == null) {
            return 0;
        }

        for (int index = 0;
             index < users.size();
             index++) {

            if (users.get(index)
                    .getId()
                    .equals(idAfter)) {

                return index + 1;
            }
        }

        return 0;
    }

    private CursorPageResponseDto<WatchingSessionDto>
    createEmptyResponse() {
        return new CursorPageResponseDto<>(
                List.of(),
                null,
                null,
                false,
                0,
                "createdAt",
                Direction.DESCENDING
        );
    }
}
