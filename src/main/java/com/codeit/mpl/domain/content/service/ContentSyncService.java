package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.TmdbProperties;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentSourceType;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.codeit.mpl.domain.content.mapper.TmdbGenreMapper;


@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSyncService {

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final TmdbProperties tmdbProperties;

    @Value("${admin.email}")
    private String adminEmail;

    /**
     * TMDB 영화 또는 TV 목록 한 페이지를 DB와 동기화합니다.
     */
    @Transactional
    public ContentSyncResult syncTmdbPage(
            ContentType type,
            List<TmdbContentItem> sourceItems
    ) {
        validateTmdbContentType(type);

        if (sourceItems == null || sourceItems.isEmpty()) {
            return ContentSyncResult.empty();
        }

        String sourceType =
                getTmdbSourceType(type);

        Map<String, TmdbContentItem> itemsByExternalId =
                new LinkedHashMap<>();

        int skippedCount = 0;

        for (TmdbContentItem item : sourceItems) {
            if (!isValidTmdbItem(type, item)) {
                skippedCount++;
                continue;
            }

            itemsByExternalId.put(
                    String.valueOf(item.id()),
                    item
            );
        }

        if (itemsByExternalId.isEmpty()) {
            return new ContentSyncResult(
                    0,
                    0,
                    skippedCount
            );
        }

        Map<String, Content> existingByExternalId =
                contentRepository
                        .findAllBySourceTypeAndExternalIdIn(
                                sourceType,
                                itemsByExternalId.keySet()
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Content::getExternalId,
                                        Function.identity()
                                )
                        );

        User admin = getAdmin();

        List<Content> newContents =
                new ArrayList<>();

        int createdCount = 0;
        int updatedCount = 0;

        for (Map.Entry<String, TmdbContentItem> entry
                : itemsByExternalId.entrySet()) {

            String externalId =
                    entry.getKey();

            TmdbContentItem item =
                    entry.getValue();

            TmdbContentData data =
                    convertTmdbItem(
                            type,
                            externalId,
                            item
                    );

            Content existingContent =
                    existingByExternalId.get(
                            externalId
                    );

            if (existingContent == null) {
                Content newContent =
                        Content.createFromExternalApi(
                                admin,
                                type,
                                data.title(),
                                data.description(),
                                data.thumbnailUrl(),
                                data.contentUrl(),
                                externalId,
                                sourceType,
                                data.tags()
                        );

                newContents.add(newContent);
                createdCount++;

                continue;
            }

            existingContent.updateFromExternalApi(
                    data.title(),
                    data.description(),
                    data.thumbnailUrl(),
                    data.contentUrl(),
                    data.tags()
            );

            updatedCount++;
        }

        if (!newContents.isEmpty()) {
            contentRepository.saveAll(
                    newContents
            );
        }

        return new ContentSyncResult(
                createdCount,
                updatedCount,
                skippedCount
        );
    }

    /**
     * SportsDB 경기 목록을 DB와 동기화합니다.
     */
    @Transactional
    public ContentSyncResult syncSportsEvents(
            List<SportsDbEventItem> sourceEvents
    ) {
        if (sourceEvents == null
                || sourceEvents.isEmpty()) {

            return ContentSyncResult.empty();
        }

        Map<String, SportsDbEventItem> eventsByExternalId =
                new LinkedHashMap<>();

        int skippedCount = 0;

        for (SportsDbEventItem event : sourceEvents) {
            if (!isValidSportsEvent(event)) {
                skippedCount++;
                continue;
            }

            eventsByExternalId.put(
                    event.idEvent(),
                    event
            );
        }

        if (eventsByExternalId.isEmpty()) {
            return new ContentSyncResult(
                    0,
                    0,
                    skippedCount
            );
        }

        Map<String, Content> existingByExternalId =
                contentRepository
                        .findAllBySourceTypeAndExternalIdIn(
                                ContentSourceType.THE_SPORTS_DB,
                                eventsByExternalId.keySet()
                        )
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        Content::getExternalId,
                                        Function.identity()
                                )
                        );

        User admin = getAdmin();

        List<Content> newContents =
                new ArrayList<>();

        int createdCount = 0;
        int updatedCount = 0;

        for (Map.Entry<String, SportsDbEventItem> entry
                : eventsByExternalId.entrySet()) {

            String externalId =
                    entry.getKey();

            SportsDbEventItem event =
                    entry.getValue();

            SportsContentData data =
                    convertSportsEvent(
                            externalId,
                            event
                    );

            Content existingContent =
                    existingByExternalId.get(
                            externalId
                    );

            if (existingContent == null) {
                Content newContent =
                        Content.createFromExternalApi(
                                admin,
                                ContentType.SPORT,
                                data.title(),
                                data.description(),
                                data.thumbnailUrl(),
                                data.contentUrl(),
                                externalId,
                                ContentSourceType.THE_SPORTS_DB,
                                data.tags()
                        );

                newContents.add(newContent);
                createdCount++;

                continue;
            }

            /*
             * 기존 스포츠 콘텐츠도 최신 정보로 갱신합니다.
             *
             * 이전에 thumbnailUrl이 비어 있던 데이터도
             * 새 이미지 선택 로직에 따라 업데이트됩니다.
             */
            existingContent.updateFromExternalApi(
                    data.title(),
                    data.description(),
                    data.thumbnailUrl(),
                    data.contentUrl(),
                    data.tags()
            );

            updatedCount++;
        }

        if (!newContents.isEmpty()) {
            contentRepository.saveAll(
                    newContents
            );
        }

        return new ContentSyncResult(
                createdCount,
                updatedCount,
                skippedCount
        );
    }

    /**
     * TMDB 동기화에서 허용하는 콘텐츠 타입인지 검증합니다.
     */
    private void validateTmdbContentType(
            ContentType type
    ) {
        if (type != ContentType.MOVIE
                && type != ContentType.TVSERIES) {

            throw new IllegalArgumentException(
                    "TMDB 동기화는 MOVIE 또는 TVSERIES만 지원합니다."
            );
        }
    }

    /**
     * 배치가 생성하는 콘텐츠의 creator로 사용할 관리자 계정을 조회합니다.
     */
    private User getAdmin() {
        return userRepository
                .findByEmail(adminEmail)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "콘텐츠 배치용 관리자 계정이 존재하지 않습니다: "
                                        + adminEmail
                        )
                );
    }

    /**
     * 영화와 TV의 externalId 영역이 서로 다르므로 sourceType도 구분합니다.
     */
    private String getTmdbSourceType(
            ContentType type
    ) {
        return switch (type) {
            case MOVIE ->
                    ContentSourceType.TMDB_MOVIE;

            case TVSERIES ->
                    ContentSourceType.TMDB_TV;

            case SPORT ->
                    throw new IllegalArgumentException(
                            "SPORT 타입은 TMDB sourceType을 사용할 수 없습니다."
                    );
        };
    }

    /**
     * 저장 가능한 TMDB 데이터인지 확인합니다.
     */
    private boolean isValidTmdbItem(
            ContentType type,
            TmdbContentItem item
    ) {
        if (item == null || item.id() == null) {
            return false;
        }

        String title =
                type == ContentType.MOVIE
                        ? item.title()
                        : item.name();

        return title != null
                && !title.isBlank();
    }

    /**
     * 저장 가능한 스포츠 경기 데이터인지 확인합니다.
     */
    private boolean isValidSportsEvent(
            SportsDbEventItem event
    ) {
        return event != null
                && event.idEvent() != null
                && !event.idEvent().isBlank()
                && event.strEvent() != null
                && !event.strEvent().isBlank();
    }

    /**
     * TMDB 응답 데이터를 Content 저장에 필요한 형태로 변환합니다.
     */
    private TmdbContentData convertTmdbItem(
            ContentType type,
            String externalId,
            TmdbContentItem item
    ) {
        String title =
                type == ContentType.MOVIE
                        ? item.title()
                        : item.name();

        String thumbnailUrl =
                buildTmdbImageUrl(
                        item.poster_path()
                );

        String contentUrl =
                type == ContentType.MOVIE
                        ? "https://www.themoviedb.org/movie/"
                        + externalId
                        : "https://www.themoviedb.org/tv/"
                        + externalId;

        return new TmdbContentData(
                title,
                item.overview(),
                thumbnailUrl,
                contentUrl,
                TmdbGenreMapper.toGenreNames(
                        type,
                        item.genre_ids()
                )
        );
    }

    /**
     * SportsDB 경기 데이터를 Content 저장 형태로 변환합니다.
     */
    private SportsContentData convertSportsEvent(
            String externalId,
            SportsDbEventItem event
    ) {
        String thumbnailUrl =
                resolveSportsThumbnailUrl(event);

        log.info(
                "SportsDB 이미지 확인: "
                        + "eventId={}, event={}, "
                        + "thumb={}, poster={}, square={}, fanart={}, banner={}, "
                        + "homeBadge={}, awayBadge={}, leagueBadge={}",
                externalId,
                event.strEvent(),
                event.strThumb(),
                event.strPoster(),
                event.strSquare(),
                event.strFanart(),
                event.strBanner(),
                event.strHomeTeamBadge(),
                event.strAwayTeamBadge(),
                event.strLeagueBadge()
        );

        log.info(
                "SportsDB 최종 썸네일 선택: eventId={}, thumbnailUrl={}",
                externalId,
                thumbnailUrl
        );

        if (!isNotBlank(thumbnailUrl)) {
            log.warn(
                    "SportsDB에서 사용할 수 있는 이미지를 찾지 못했습니다: "
                            + "eventId={}, event={}, league={}",
                    externalId,
                    event.strEvent(),
                    event.strLeague()
            );
        }

        List<String> tags =
                new ArrayList<>();

        tags.add(
                ContentType.SPORT.name()
        );

        addTagIfPresent(
                tags,
                event.strSport()
        );

        addTagIfPresent(
                tags,
                event.strLeague()
        );

        addTagIfPresent(
                tags,
                event.strSeason()
        );

        return new SportsContentData(
                event.strEvent(),
                createSportsDescription(event),
                thumbnailUrl,
                "https://www.thesportsdb.com/event/"
                        + externalId,
                tags
        );
    }

    /**
     * 스포츠 이미지 선택 우선순위입니다.
     *
     * 1. 경기 썸네일
     * 2. 경기 포스터
     * 3. 경기 정사각형 이미지
     * 4. 경기 팬아트
     * 5. 경기 배너
     * 6. 홈팀 배지
     * 7. 원정팀 배지
     * 8. 리그 배지
     */
    private String resolveSportsThumbnailUrl(
            SportsDbEventItem event
    ) {
        return firstNonBlank(
                event.strThumb(),
                event.strPoster(),
                event.strSquare(),
                event.strFanart(),
                event.strBanner(),
                event.strHomeTeamBadge(),
                event.strAwayTeamBadge(),
                event.strLeagueBadge()
        );
    }

    /**
     * TMDB의 상대 포스터 경로를 전체 URL로 변환합니다.
     */
    private String buildTmdbImageUrl(
            String posterPath
    ) {
        if (posterPath == null
                || posterPath.isBlank()) {

            return null;
        }

        return tmdbProperties.imageBaseUrl()
                + posterPath;
    }

    /**
     * 스포츠 경기 설명을 생성합니다.
     */
    private String createSportsDescription(
            SportsDbEventItem event
    ) {
        List<String> descriptions =
                new ArrayList<>();

        addDescription(
                descriptions,
                "리그",
                event.strLeague()
        );

        addDescription(
                descriptions,
                "시즌",
                event.strSeason()
        );

        if (isNotBlank(event.strHomeTeam())
                && isNotBlank(event.strAwayTeam())) {

            descriptions.add(
                    "경기: "
                            + event.strHomeTeam()
                            + " vs "
                            + event.strAwayTeam()
            );
        }

        addDescription(
                descriptions,
                "날짜",
                event.dateEvent()
        );

        addDescription(
                descriptions,
                "시간",
                event.strTime()
        );

        addDescription(
                descriptions,
                "장소",
                event.strVenue()
        );

        if (isNotBlank(
                event.strDescriptionEN()
        )) {
            descriptions.add(
                    event.strDescriptionEN()
            );
        }

        return String.join(
                "\n",
                descriptions
        );
    }

    private void addTagIfPresent(
            List<String> tags,
            String value
    ) {
        if (isNotBlank(value)
                && !tags.contains(value.trim())) {

            tags.add(value.trim());
        }
    }

    private void addDescription(
            List<String> descriptions,
            String label,
            String value
    ) {
        if (isNotBlank(value)) {
            descriptions.add(
                    label + ": " + value.trim()
            );
        }
    }

    /**
     * 전달된 값 중 비어 있지 않은 첫 번째 값을 반환합니다.
     */
    private String firstNonBlank(
            String... values
    ) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (isNotBlank(value)) {
                return value.trim();
            }
        }

        return null;
    }

    private boolean isNotBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private record TmdbContentData(
            String title,
            String description,
            String thumbnailUrl,
            String contentUrl,
            List<String> tags
    ) {
    }

    private record SportsContentData(
            String title,
            String description,
            String thumbnailUrl,
            String contentUrl,
            List<String> tags
    ) {
    }
}