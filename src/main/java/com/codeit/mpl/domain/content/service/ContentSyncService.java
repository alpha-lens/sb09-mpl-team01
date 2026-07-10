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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * 동작 방식:
     * 1. 외부 ID가 없거나 제목이 없는 데이터는 제외합니다.
     * 2. sourceType + externalId로 기존 데이터를 한 번에 조회합니다.
     * 3. 기존 콘텐츠가 없으면 INSERT합니다.
     * 4. 기존 콘텐츠가 있으면 최신 외부 데이터로 UPDATE합니다.
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

        String sourceType = getTmdbSourceType(type);

        /*
         * 같은 TMDB 페이지 안에 중복 externalId가 포함되어도
         * 한 번만 처리되도록 Map으로 정리합니다.
         */
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

        /*
         * 페이지의 externalId들을 한 번에 조회합니다.
         *
         * 각 콘텐츠마다 SELECT하는 방식보다 DB 조회 횟수를 줄일 수 있습니다.
         */
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

            /*
             * DB에 없는 콘텐츠는 새로 생성합니다.
             */
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

            /*
             * 이미 DB에 존재하면 제목, 설명, 썸네일 등의
             * 외부 API 최신 데이터로 갱신합니다.
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
     * SportsDB 경기 목록을 DB와 동기화합니다.
     *
     * 기존 경기는 경기 일정, 장소, 썸네일 등의 최신 정보로 갱신합니다.
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
                List.of(type.name())
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
                firstNonBlank(
                        event.strThumb(),
                        event.strPoster(),
                        event.strBanner()
                );

        List<String> tags =
                new ArrayList<>();

        tags.add(ContentType.SPORT.name());

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
                && !tags.contains(value)) {

            tags.add(value);
        }
    }

    private void addDescription(
            List<String> descriptions,
            String label,
            String value
    ) {
        if (isNotBlank(value)) {
            descriptions.add(
                    label + ": " + value
            );
        }
    }

    private String firstNonBlank(
            String... values
    ) {
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
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