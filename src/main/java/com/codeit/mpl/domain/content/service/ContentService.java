package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.client.TmdbProperties;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbTeamResponse;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
import com.codeit.mpl.domain.content.dto.query.ContentQueryRow;
import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentSourceType;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.WatchingSessionRepository;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentService {

    /*
     * TMDB 영화와 TV는 같은 숫자 ID를 가질 수 있습니다.
     *
     * 예:
     * 영화 externalId = 100
     * TV externalId = 100
     *
     * sourceType을 단순히 TMDB 하나로 사용하면
     * UNIQUE(source_type, external_id) 조건에서 충돌할 수 있으므로
     * 영화와 TV를 서로 다른 sourceType으로 분리합니다.
     */
    private static final String TMDB_MOVIE_SOURCE_TYPE =
            ContentSourceType.TMDB_MOVIE;

    private static final String TMDB_TV_SOURCE_TYPE =
            ContentSourceType.TMDB_TV;

    private static final String SPORTS_DB_SOURCE_TYPE =
            ContentSourceType.THE_SPORTS_DB;

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;
    private final ReviewRepository reviewRepository;
    private final WatchingSessionRepository watchingSessionRepository;
    private final TmdbClient tmdbClient;
    private final SportsDbClient sportsDbClient;
    private final TmdbProperties tmdbProperties;

    /**
     * 관리자가 콘텐츠를 직접 등록합니다.
     */
    public ContentDto createContent(
            String requesterEmail,
            ContentCreateRequest request
    ) {
        User creator =
                getRequester(requesterEmail);

        validateAdmin(creator);

        Content content =
                Content.create(
                        creator,
                        request.type(),
                        request.title(),
                        request.description(),
                        null,
                        null,
                        request.tags()
                );

        Content savedContent =
                contentRepository.save(content);

        return toDto(savedContent);
    }

    /**
     * 관리자가 외부 API 검색 결과를 수동으로 가져옵니다.
     *
     * 동일한 sourceType과 externalId가 이미 저장되어 있으면
     * 새로 저장하지 않고 기존 콘텐츠를 반환합니다.
     */
    public ContentDto importExternalContent(
            String requesterEmail,
            ContentImportRequest request
    ) {
        User requester =
                getRequester(requesterEmail);

        validateAdmin(requester);

        String sourceType =
                getSourceType(request.type());

        return contentRepository
                .findBySourceTypeAndExternalId(
                        sourceType,
                        request.externalId()
                )
                .map(this::toDto)
                .orElseGet(() ->
                        importNewExternalContent(
                                requester,
                                request,
                                sourceType
                        )
                );
    }

    /**
     * DB에 존재하지 않는 외부 콘텐츠를 새로 저장합니다.
     */
    private ContentDto importNewExternalContent(
            User requester,
            ContentImportRequest request,
            String sourceType
    ) {
        Content content =
                switch (request.type()) {
                    case MOVIE, TVSERIES -> {
                        TmdbContentItem item =
                                switch (request.type()) {
                                    case MOVIE ->
                                            tmdbClient.getMovieDetail(
                                                    request.externalId()
                                            );

                                    case TVSERIES ->
                                            tmdbClient.getTvSeriesDetail(
                                                    request.externalId()
                                            );

                                    case SPORT ->
                                            throw new IllegalArgumentException(
                                                    "SPORT 타입은 TMDB import를 지원하지 않습니다."
                                            );
                                };

                        if (item == null
                                || item.id() == null) {

                            throw new IllegalArgumentException(
                                    "존재하지 않는 TMDB 콘텐츠입니다."
                            );
                        }

                        yield createContentFromTmdb(
                                requester,
                                request.type(),
                                request.externalId(),
                                sourceType,
                                item
                        );
                    }

                    case SPORT -> {
                        SportsDbEventItem item =
                                getSportsEventItem(
                                        request.externalId()
                                );

                        yield createContentFromSportsDb(
                                requester,
                                request.externalId(),
                                sourceType,
                                item
                        );
                    }
                };

        try {
            Content savedContent =
                    contentRepository.saveAndFlush(
                            content
                    );

            return toDto(savedContent);

        } catch (DataIntegrityViolationException e) {
            /*
             * 여러 요청 또는 여러 서버 인스턴스가 같은 콘텐츠를
             * 동시에 저장하려고 한 경우 DB UNIQUE 제약조건에서
             * 한 요청이 실패할 수 있습니다.
             *
             * 이 경우 이미 저장된 콘텐츠를 다시 조회해 반환합니다.
             */
            Content existingContent =
                    contentRepository
                            .findBySourceTypeAndExternalId(
                                    sourceType,
                                    request.externalId()
                            )
                            .orElseThrow(() -> e);

            return toDto(existingContent);
        }
    }

    /**
     * 콘텐츠 단건 조회입니다.
     */
    @Transactional(readOnly = true)
    public ContentDto getContent(
            UUID contentId
    ) {
        Content content =
                getContentEntity(contentId);

        return toDto(content);
    }

    /**
     * 콘텐츠를 수정합니다.
     */
    public ContentDto updateContent(
            String requesterEmail,
            UUID contentId,
            ContentUpdateRequest request
    ) {
        User requester =
                getRequester(requesterEmail);

        Content content =
                getContentEntity(contentId);

        validateContentOwnerOrAdmin(
                requester,
                content
        );

        content.update(
                request.title(),
                request.description(),
                request.tags()
        );

        return toDto(content);
    }

    /**
     * 콘텐츠를 삭제합니다.
     */
    public void deleteContent(
            String requesterEmail,
            UUID contentId
    ) {
        User requester =
                getRequester(requesterEmail);

        Content content =
                getContentEntity(contentId);

        validateContentOwnerOrAdmin(
                requester,
                content
        );

        contentRepository.delete(content);
    }

    /**
     * 콘텐츠 목록을 QueryDSL 기반 커서 페이지네이션으로 조회합니다.
     *
     * createdAt, watcherCount, rate 정렬을 모두 DB에서 처리합니다.
     */
    @Transactional(readOnly = true)
    public CursorPageResponseDto<ContentSummary> getContents(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        validateCursorPair(
                cursor,
                idAfter
        );

        validateSortBy(sortBy);

        UUID parsedIdAfter =
                idAfter == null
                        || idAfter.isBlank()
                        ? null
                        : parseIdAfter(idAfter);

        List<ContentQueryRow> queryRows =
                contentRepository.findContents(
                        cursor,
                        parsedIdAfter,
                        keywordLike,
                        type,
                        limit,
                        sortBy,
                        sortDirection
                );

        boolean hasNext =
                queryRows.size() > limit;

        List<ContentQueryRow> pageRows =
                hasNext
                        ? queryRows.subList(
                        0,
                        limit
                )
                        : queryRows;

        List<ContentSummary> contentSummaries =
                pageRows.stream()
                        .map(row ->
                                contentMapper.toSummary(
                                        row.content(),
                                        row.averageRating(),
                                        row.reviewCount()
                                )
                        )
                        .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageRows.isEmpty()) {
            ContentQueryRow lastRow =
                    pageRows.get(
                            pageRows.size() - 1
                    );

            nextCursor =
                    getQueryCursorValue(
                            lastRow,
                            sortBy
                    );

            nextIdAfter =
                    lastRow.content()
                            .getId()
                            .toString();
        }

        long totalCount =
                contentRepository.countContents(
                        keywordLike,
                        type
                );

        return new CursorPageResponseDto<>(
                contentSummaries,
                nextCursor,
                nextIdAfter,
                hasNext,
                totalCount,
                sortBy,
                sortDirection
        );
    }

    /**
     * 현재 페이지의 마지막 콘텐츠를 기준으로 다음 cursor를 생성합니다.
     */
    private String getQueryCursorValue(
            ContentQueryRow row,
            String sortBy
    ) {
        return switch (sortBy) {
            case "createdAt" ->
                    row.content()
                            .getCreatedAt()
                            .toString();

            case "watcherCount" ->
                    String.valueOf(
                            row.watcherCount()
                    );

            case "rate" ->
                    String.valueOf(
                            row.averageRating()
                    );

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 정렬 기준입니다."
                    );
        };
    }

    /**
     * 관리자가 외부 API에서 콘텐츠를 검색합니다.
     */
    @Transactional(readOnly = true)
    public List<ExternalContentSearchResult>
    searchExternalContents(
            String keyword,
            ContentType type
    ) {
        if (type == ContentType.SPORT) {
            return searchSportsContents(
                    keyword
            );
        }

        TmdbSearchResponse response =
                switch (type) {
                    case MOVIE ->
                            tmdbClient.searchMovies(
                                    keyword
                            );

                    case TVSERIES ->
                            tmdbClient.searchTvSeries(
                                    keyword
                            );

                    case SPORT ->
                            throw new IllegalArgumentException(
                                    "SPORT 타입은 SportsDB 검색을 사용해야 합니다."
                            );
                };

        if (response == null
                || response.results() == null) {

            return List.of();
        }

        return response.results()
                .stream()
                .filter(item ->
                        item != null
                                && item.id() != null
                )
                .map(item ->
                        toExternalSearchResult(
                                item,
                                type
                        )
                )
                .toList();
    }

    /**
     * SportsDB에서 팀을 검색하고 해당 팀의 예정 경기를 반환합니다.
     */
    private List<ExternalContentSearchResult>
    searchSportsContents(
            String keyword
    ) {
        SportsDbTeamResponse teamResponse =
                sportsDbClient.searchTeams(
                        keyword
                );

        if (teamResponse == null
                || teamResponse.teams() == null
                || teamResponse.teams().isEmpty()) {

            return List.of();
        }

        /*
         * 검색된 모든 팀의 예정 경기를 조회합니다.
         *
         * 팀 개수와 팀별 경기 개수에는 임의 제한을 적용하지 않습니다.
         * 같은 경기가 여러 팀 검색 결과에 포함될 수 있으므로
         * SportsDB의 idEvent(externalId) 기준으로만 중복을 제거합니다.
         */
        Map<String, ExternalContentSearchResult>
                resultsByExternalId =
                new LinkedHashMap<>();

        teamResponse.teams()
                .stream()
                .filter(team ->
                        team != null
                                && team.idTeam() != null
                                && !team.idTeam().isBlank()
                )
                .forEach(team -> {
                    SportsDbEventResponse eventResponse =
                            sportsDbClient
                                    .getNextEventsByTeam(
                                            team.idTeam()
                                    );

                    if (eventResponse == null
                            || eventResponse.events() == null
                            || eventResponse.events().isEmpty()) {

                        return;
                    }

                    eventResponse.events()
                            .stream()
                            .filter(event ->
                                    event != null
                                            && event.idEvent() != null
                                            && !event.idEvent().isBlank()
                                            && event.strEvent() != null
                                            && !event.strEvent().isBlank()
                            )
                            .map(
                                    this::toSportsExternalSearchResult
                            )
                            .forEach(result ->
                                    resultsByExternalId.putIfAbsent(
                                            result.externalId(),
                                            result
                                    )
                            );
                });

        return List.copyOf(
                resultsByExternalId.values()
        );
    }

    /**
     * TMDB 검색 결과를 API 응답 DTO로 변환합니다.
     */
    private ExternalContentSearchResult
    toExternalSearchResult(
            TmdbContentItem item,
            ContentType type
    ) {
        String title =
                type == ContentType.MOVIE
                        ? item.title()
                        : item.name();

        String releaseDate =
                type == ContentType.MOVIE
                        ? item.release_date()
                        : item.first_air_date();

        String thumbnailUrl =
                buildTmdbImageUrl(
                        item.poster_path()
                );

        return new ExternalContentSearchResult(
                String.valueOf(item.id()),
                type,
                title,
                item.overview(),
                thumbnailUrl,
                releaseDate
        );
    }

    /**
     * SportsDB 경기 데이터를 외부 검색 응답으로 변환합니다.
     */
    private ExternalContentSearchResult
    toSportsExternalSearchResult(
            SportsDbEventItem event
    ) {
        String thumbnailUrl =
                resolveSportsThumbnailUrl(
                        event
                );

        return new ExternalContentSearchResult(
                event.idEvent(),
                ContentType.SPORT,
                event.strEvent(),
                createSportsDescription(event),
                thumbnailUrl,
                event.dateEvent()
        );
    }

    /**
     * TMDB 상세 조회 결과를 Content 엔티티로 변환합니다.
     */
    private Content createContentFromTmdb(
            User creator,
            ContentType type,
            String externalId,
            String sourceType,
            TmdbContentItem item
    ) {
        String title =
                type == ContentType.MOVIE
                        ? item.title()
                        : item.name();

        if (title == null
                || title.isBlank()) {

            throw new IllegalArgumentException(
                    "TMDB 콘텐츠 제목이 존재하지 않습니다."
            );
        }

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

        List<String> tags =
                new ArrayList<>();

        tags.add(
                type.name()
        );

        return Content.createFromExternalApi(
                creator,
                type,
                title,
                item.overview(),
                thumbnailUrl,
                contentUrl,
                externalId,
                sourceType,
                tags
        );
    }

    /**
     * SportsDB 경기 데이터를 Content 엔티티로 변환합니다.
     */
    private Content createContentFromSportsDb(
            User creator,
            String externalId,
            String sourceType,
            SportsDbEventItem item
    ) {
        String thumbnailUrl =
                resolveSportsThumbnailUrl(
                        item
                );

        String contentUrl =
                "https://www.thesportsdb.com/event/"
                        + externalId;

        List<String> tags =
                new ArrayList<>();

        tags.add(
                ContentType.SPORT.name()
        );

        addTagIfPresent(
                tags,
                item.strSport()
        );

        addTagIfPresent(
                tags,
                item.strLeague()
        );

        addTagIfPresent(
                tags,
                item.strSeason()
        );

        return Content.createFromExternalApi(
                creator,
                ContentType.SPORT,
                item.strEvent(),
                createSportsDescription(item),
                thumbnailUrl,
                contentUrl,
                externalId,
                sourceType,
                tags
        );
    }

    /**
     * SportsDB 경기 상세를 조회합니다.
     */
    private SportsDbEventItem getSportsEventItem(
            String externalId
    ) {
        SportsDbEventResponse response =
                sportsDbClient
                        .getEventDetail(
                                externalId
                        );

        if (response == null
                || response.events() == null
                || response.events().isEmpty()) {

            throw new IllegalArgumentException(
                    "존재하지 않는 스포츠 경기입니다."
            );
        }

        SportsDbEventItem item =
                response.events().get(0);

        if (item == null
                || item.idEvent() == null
                || item.idEvent().isBlank()
                || item.strEvent() == null
                || item.strEvent().isBlank()) {

            throw new IllegalArgumentException(
                    "스포츠 경기 정보가 올바르지 않습니다."
            );
        }

        return item;
    }

    /**
     * 스포츠 경기 설명을 생성합니다.
     */
    private String createSportsDescription(
            SportsDbEventItem item
    ) {
        List<String> descriptions =
                new ArrayList<>();

        addDescription(
                descriptions,
                "리그",
                item.strLeague()
        );

        addDescription(
                descriptions,
                "시즌",
                item.strSeason()
        );

        if (isNotBlank(item.strHomeTeam())
                && isNotBlank(item.strAwayTeam())) {

            descriptions.add(
                    "경기: "
                            + item.strHomeTeam()
                            + " vs "
                            + item.strAwayTeam()
            );
        }

        addDescription(
                descriptions,
                "날짜",
                item.dateEvent()
        );

        addDescription(
                descriptions,
                "시간",
                item.strTime()
        );

        addDescription(
                descriptions,
                "장소",
                item.strVenue()
        );

        if (isNotBlank(
                item.strDescriptionEN()
        )) {
            descriptions.add(
                    item.strDescriptionEN()
            );
        }

        return String.join(
                "\n",
                descriptions
        );
    }

    /**
     * SportsDB 이미지 선택 우선순위입니다.
     *
     * 경기 이미지가 없을 경우 팀 배지를 사용하고,
     * 팀 배지도 없을 경우 리그 배지를 사용합니다.
     */
    private String resolveSportsThumbnailUrl(
            SportsDbEventItem item
    ) {
        return firstNonBlank(
                item.strThumb(),
                item.strPoster(),
                item.strSquare(),
                item.strFanart(),
                item.strBanner(),
                item.strHomeTeamBadge(),
                item.strAwayTeamBadge(),
                item.strLeagueBadge()
        );
    }

    /**
     * 콘텐츠 타입에 따라 외부 소스 타입을 반환합니다.
     */
    private String getSourceType(
            ContentType type
    ) {
        return switch (type) {
            case MOVIE ->
                    TMDB_MOVIE_SOURCE_TYPE;

            case TVSERIES ->
                    TMDB_TV_SOURCE_TYPE;

            case SPORT ->
                    SPORTS_DB_SOURCE_TYPE;
        };
    }

    /**
     * TMDB 포스터 경로를 전체 URL로 변환합니다.
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
     * 전달된 문자열 중 비어 있지 않은 첫 번째 값을 반환합니다.
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

    private void addTagIfPresent(
            List<String> tags,
            String value
    ) {
        if (isNotBlank(value)
                && !tags.contains(value.trim())) {

            tags.add(
                    value.trim()
            );
        }
    }

    private void addDescription(
            List<String> descriptions,
            String label,
            String value
    ) {
        if (isNotBlank(value)) {
            descriptions.add(
                    label
                            + ": "
                            + value.trim()
            );
        }
    }

    private boolean isNotBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    /**
     * 요청자 이메일을 통해 사용자를 조회합니다.
     */
    private User getRequester(
            String email
    ) {
        if (email == null
                || email.isBlank()) {

            throw new IllegalArgumentException(
                    "인증 정보가 유효하지 않습니다."
            );
        }

        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 사용자입니다."
                        )
                );
    }

    /**
     * 관리자 권한을 검증합니다.
     */
    private void validateAdmin(
            User requester
    ) {
        if (requester.getRole()
                != UserRole.ADMIN) {

            throw new IllegalArgumentException(
                    "관리자만 콘텐츠를 등록할 수 있습니다."
            );
        }
    }

    /**
     * 콘텐츠 소유자 또는 관리자 여부를 검증합니다.
     */
    private void validateContentOwnerOrAdmin(
            User requester,
            Content content
    ) {
        boolean isOwner =
                content.getCreator()
                        .getId()
                        .equals(
                                requester.getId()
                        );

        boolean isAdmin =
                requester.getRole()
                        == UserRole.ADMIN;

        if (!isOwner
                && !isAdmin) {

            throw new IllegalArgumentException(
                    "콘텐츠를 수정하거나 삭제할 권한이 없습니다."
            );
        }
    }

    /**
     * 콘텐츠 엔티티를 조회합니다.
     */
    private Content getContentEntity(
            UUID contentId
    ) {
        return contentRepository
                .findById(contentId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 콘텐츠입니다."
                        )
                );
    }

    /**
     * cursor와 idAfter가 함께 전달됐는지 검증합니다.
     */
    private void validateCursorPair(
            String cursor,
            String idAfter
    ) {
        boolean hasCursor =
                cursor != null
                        && !cursor.isBlank();

        boolean hasIdAfter =
                idAfter != null
                        && !idAfter.isBlank();

        if (hasCursor != hasIdAfter) {
            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다."
            );
        }
    }

    /**
     * idAfter 문자열을 UUID로 변환합니다.
     */
    private UUID parseIdAfter(
            String idAfter
    ) {
        try {
            return UUID.fromString(
                    idAfter
            );

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "idAfter는 올바른 UUID 형식이어야 합니다."
            );
        }
    }

    /**
     * 지원하는 정렬 기준인지 검증합니다.
     */
    private void validateSortBy(
            String sortBy
    ) {
        if (!"createdAt".equals(sortBy)
                && !"watcherCount".equals(sortBy)
                && !"rate".equals(sortBy)) {

            throw new IllegalArgumentException(
                    "sortBy는 createdAt, watcherCount, rate만 사용할 수 있습니다."
            );
        }
    }

    /**
     * Content 엔티티를 상세 DTO로 변환합니다.
     */
    private ContentDto toDto(
            Content content
    ) {
        Double averageRating =
                reviewRepository
                        .findAverageRatingByContent(
                                content
                        );

        if (averageRating == null) {
            averageRating = 0.0;
        }

        Integer reviewCount =
                Math.toIntExact(
                        reviewRepository
                                .countByContent(
                                        content
                                )
                );

        Long watcherCount =
                watchingSessionRepository
                        .countByContent(
                                content
                        );

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount
        );
    }

    /**
     * Content 엔티티를 목록 요약 DTO로 변환합니다.
     *
     * 현재 QueryDSL 목록 조회에서는 집계값을 한 번에 조회하므로
     * 기본 목록 API에서는 직접 호출하지 않습니다.
     *
     * 다른 내부 로직에서 단일 ContentSummary 변환이 필요한 경우를 위해
     * 기존 메서드를 유지합니다.
     */
    private ContentSummary toSummary(
            Content content
    ) {
        Double averageRating =
                reviewRepository
                        .findAverageRatingByContent(
                                content
                        );

        if (averageRating == null) {
            averageRating = 0.0;
        }

        Integer reviewCount =
                Math.toIntExact(
                        reviewRepository
                                .countByContent(
                                        content
                                )
                );

        return contentMapper.toSummary(
                content,
                averageRating,
                reviewCount
        );
    }
}