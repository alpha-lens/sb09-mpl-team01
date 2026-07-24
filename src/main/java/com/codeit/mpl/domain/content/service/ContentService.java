package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.dto.external.*;
import com.codeit.mpl.domain.content.dto.query.ContentQueryRow;
import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.event.ContentEvent;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.curating.repository.PlaylistContentRepository;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentService {

    private static final String TMDB_IMAGE_BASE_URL =
            "https://image.tmdb.org/t/p/w500";

    private static final String TMDB_SOURCE_TYPE =
            "TMDB";

    private static final String SPORTS_DB_SOURCE_TYPE =
            "THE_SPORTS_DB";

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;
    private final TmdbClient tmdbClient;
    private final SportsDbClient sportsDbClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ContentSearchService contentSearchService;
    private final PlaylistContentRepository playlistContentRepository;
    private final ReviewRepository reviewRepository;
    private final BinaryContentStorage binaryContentStorage;

    public ContentDto createContent(
            String requesterEmail,
            ContentCreateRequest request
    ) {
        return createContent(requesterEmail, request, null);
    }

    public ContentDto createContent(
            String requesterEmail,
            ContentCreateRequest request,
            MultipartFile thumbnail
    ) {
        User creator =
                getRequester(
                        requesterEmail
                );

        validateAdmin(
                creator
        );

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

        if (thumbnail != null && !thumbnail.isEmpty()) {
            validateThumbnailContentType(thumbnail);
            String key = "content-thumbnails/" + UUID.randomUUID()
                    + extractSafeExtension(thumbnail.getOriginalFilename());
            String storedKey = binaryContentStorage.put(key, thumbnail);
            content.updateThumbnailUrl(storedKey);
            registerCleanupOnRollback(storedKey);
        }

        Content savedContent =
                contentRepository.save(
                        content
                );

        eventPublisher.publishEvent(
                new ContentEvent(
                        savedContent,
                        ContentEvent.EventType.CREATED
                )
        );

        return toDto(
                savedContent
        );
    }

    public ContentDto importExternalContent(
            String requesterEmail,
            ContentImportRequest request
    ) {
        User requester =
                getRequester(
                        requesterEmail
                );

        validateAdmin(
                requester
        );

        String sourceType =
                getSourceType(
                        request.type()
                );

        return contentRepository
                .findBySourceTypeAndExternalId(
                        sourceType,
                        request.externalId()
                )
                .map(
                        this::toDto
                )
                .orElseGet(
                        () ->
                                importNewExternalContent(
                                        requester,
                                        request,
                                        sourceType
                                )
                );
    }

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
                                            tmdbClient
                                                    .getMovieDetail(
                                                            request.externalId()
                                                    );

                                    case TVSERIES ->
                                            tmdbClient
                                                    .getTvSeriesDetail(
                                                            request.externalId()
                                                    );

                                    case SPORT ->
                                            throw new IllegalArgumentException(
                                                    "SPORT 타입은 TMDB import를 지원하지 않습니다."
                                            );
                                };

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

            eventPublisher.publishEvent(
                    new ContentEvent(
                            savedContent,
                            ContentEvent.EventType.CREATED
                    )
            );

            return toDto(
                    savedContent
            );

        } catch (DataIntegrityViolationException exception) {
            Content existingContent =
                    contentRepository
                            .findBySourceTypeAndExternalId(
                                    sourceType,
                                    request.externalId()
                            )
                            .orElseThrow(
                                    () -> exception
                            );

            return toDto(
                    existingContent
            );
        }
    }

    @Transactional(readOnly = true)
    public ContentDto getContent(
            UUID contentId
    ) {
        Content content =
                getContentEntity(
                        contentId
                );

        return toDto(
                content
        );
    }

    public ContentDto updateContent(
            String requesterEmail,
            UUID contentId,
            ContentUpdateRequest request
    ) {
        return updateContent(requesterEmail, contentId, request, null);
    }

    public ContentDto updateContent(
            String requesterEmail,
            UUID contentId,
            ContentUpdateRequest request,
            MultipartFile thumbnail
    ) {
        User requester =
                getRequester(
                        requesterEmail
                );

        Content content =
                getContentEntity(
                        contentId
                );

        validateContentOwnerOrAdmin(
                requester,
                content
        );

        content.update(
                request.title(),
                request.description(),
                request.tags()
        );

        if (thumbnail != null && !thumbnail.isEmpty()) {
            validateThumbnailContentType(thumbnail);
            String key = "content-thumbnails/" + UUID.randomUUID()
                    + extractSafeExtension(thumbnail.getOriginalFilename());
            String storedKey = binaryContentStorage.put(key, thumbnail);
            String previousKey = content.getThumbnailUrl();
            content.updateThumbnailUrl(storedKey);
            registerCleanupOnRollback(storedKey);
            if (previousKey != null && !previousKey.isBlank()) {
                registerCleanupOnCommit(previousKey);
            }
        }

        eventPublisher.publishEvent(
                new ContentEvent(
                        content,
                        ContentEvent.EventType.UPDATED
                )
        );

        return toDto(
                content
        );
    }

    private void validateThumbnailContentType(MultipartFile thumbnail) {
        String contentType = thumbnail.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
    }

    private String extractSafeExtension(String originalFilename) {
        if (originalFilename == null || !originalFilename.contains(".")) {
            return ".png";
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        if (ext.matches("^\\.(png|jpg|jpeg|gif|webp)$")) {
            return ext;
        }
        return ".png";
    }

    private void registerCleanupOnRollback(String key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            binaryContentStorage.delete(key);
                        } catch (Exception e) {
                            log.warn("롤백 후 바이너리 파일 삭제 실패: {}", key, e);
                        }
                    }
                }
            });
        }
    }

    private void registerCleanupOnCommit(String key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        binaryContentStorage.delete(key);
                    } catch (Exception e) {
                        log.warn("커밋 후 이전 바이너리 파일 삭제 실패: {}", key, e);
                    }
                }
            });
        }
    }

    public void deleteContent(
            String requesterEmail,
            UUID contentId
    ) {
        User requester =
                getRequester(
                        requesterEmail
                );

        Content content =
                getContentEntity(
                        contentId
                );

        validateContentOwnerOrAdmin(
                requester,
                content
        );

        reviewRepository.deleteByContent(content);

        playlistContentRepository.deleteByContent(content);

        contentRepository.delete(
                content
        );

        eventPublisher.publishEvent(
                new ContentEvent(
                        content,
                        ContentEvent.EventType.DELETED
                )
        );
    }

    /**
     * 콘텐츠 목록을 조회합니다.
     *
     * 검색어가 있는 경우:
     * 1. Elasticsearch 검색을 먼저 시도합니다.
     * 2. Elasticsearch 장애가 발생하면 QueryDSL DB 검색으로 폴백합니다.
     *
     * 검색어가 없는 경우:
     * QueryDSL 기반 커서 페이지네이션을 사용합니다.
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

        validateSortBy(
                sortBy
        );

        validateLimit(
                limit
        );

        if (keywordLike != null
                && !keywordLike.isBlank()) {

            try {
                return getContentsViaElasticsearch(
                        cursor,
                        idAfter,
                        keywordLike,
                        type,
                        limit,
                        sortBy,
                        sortDirection
                );

            } catch (Exception exception) {
                log.error(
                        "Elasticsearch search failed, falling back to database search",
                        exception
                );
            }
        }

        return getContentsViaDatabase(
                cursor,
                idAfter,
                keywordLike,
                type,
                limit,
                sortBy,
                sortDirection
        );
    }

    /**
     * QueryDSL 기반 콘텐츠 목록 조회입니다.
     *
     * ContentQueryRepositoryImpl에서 다음 통계값을 계산합니다.
     *
     * - averageRating
     * - reviewCount
     * - watcherCount
     */
    private CursorPageResponseDto<ContentSummary>
    getContentsViaDatabase(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        UUID parsedIdAfter =
                idAfter == null
                        || idAfter.isBlank()
                        ? null
                        : parseIdAfter(
                        idAfter
                );

        List<ContentQueryRow> rows =
                contentRepository.findContents(
                        cursor,
                        parsedIdAfter,
                        keywordLike,
                        null,
                        type,
                        limit,
                        sortBy,
                        sortDirection
                );

        boolean hasNext =
                rows.size() > limit;

        List<ContentQueryRow> pageRows =
                hasNext
                        ? rows.subList(
                        0,
                        limit
                )
                        : rows;

        List<ContentSummary> contentSummaries =
                pageRows.stream()
                        .map(
                                this::toSummary
                        )
                        .toList();

        String nextCursor =
                null;

        String nextIdAfter =
                null;

        if (hasNext
                && !pageRows.isEmpty()) {

            ContentQueryRow lastRow =
                    pageRows.get(
                            pageRows.size() - 1
                    );

            nextCursor =
                    getCursorValue(
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
                        null,
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
     * Elasticsearch 검색 결과에 포함된 ID를 대상으로
     * QueryDSL 기반 정렬과 커서 페이지네이션을 적용합니다.
     *
     * 기존 JPA Specification + createCursorPredicate() 방식을 제거하고
     * ContentQueryRepositoryImpl.findContents()를 재사용합니다.
     * 제한 없이 매칭 ID 전체를 수집합니다(Pageable.unpaged()).
     */
    private CursorPageResponseDto<ContentSummary>
    getContentsViaElasticsearch(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        Page<ContentDocument> docsPage =
                contentSearchService.search(
                        keywordLike,
                        PageRequest.of(0, 10000)
                );

        List<ContentDocument> documents = docsPage.getContent();

        List<UUID> matchingIds =
                documents.stream()
                        .map(
                                document ->
                                        UUID.fromString(
                                                document.getId()
                                        )
                        )
                        .toList();

        if (matchingIds.isEmpty()) {
            return new CursorPageResponseDto<>(
                    List.of(),
                    null,
                    null,
                    false,
                    0L,
                    sortBy,
                    sortDirection
            );
        }

        UUID parsedIdAfter =
                idAfter == null || idAfter.isBlank()
                        ? null
                        : parseIdAfter(idAfter);

        List<ContentQueryRow> rows =
                contentRepository.findContents(
                        cursor,
                        parsedIdAfter,
                        null,
                        matchingIds,
                        type,
                        limit,
                        sortBy,
                        sortDirection
                );

        boolean hasNext =
                rows.size() > limit;

        List<ContentQueryRow> pageRows =
                hasNext
                        ? rows.subList(0, limit)
                        : rows;

        List<ContentSummary> contentSummaries =
                pageRows.stream()
                        .map(this::toSummary)
                        .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageRows.isEmpty()) {
            ContentQueryRow lastRow =
                    pageRows.get(pageRows.size() - 1);
            nextCursor = getCursorValue(lastRow, sortBy);
            nextIdAfter = lastRow.content().getId().toString();
        }

        long totalCount = contentRepository.countContents(
                null,
                matchingIds,
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



    @Transactional(readOnly = true)
    public List<ExternalContentSearchResult>
    searchExternalContents(
            String keyword,
            ContentType type
    ) {
        if (type
                == ContentType.SPORT) {

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
                .map(
                        item ->
                                toExternalSearchResult(
                                        item,
                                        type
                                )
                )
                .toList();
    }

    private List<ExternalContentSearchResult>
    searchSportsContents(
            String keyword
    ) {
        SportsDbTeamResponse teamResponse =
                sportsDbClient.searchTeams(
                        keyword
                );

        if (teamResponse == null
                || teamResponse.teams() == null) {

            return List.of();
        }

        return teamResponse.teams()
                .stream()
                .limit(
                        3
                )
                .flatMap(
                        team -> {
                            SportsDbEventResponse eventResponse =
                                    sportsDbClient
                                            .getNextEventsByTeam(
                                                    team.idTeam()
                                            );

                            if (eventResponse == null
                                    || eventResponse.events() == null) {

                                return List
                                        .<ExternalContentSearchResult>of()
                                        .stream();
                            }

                            return eventResponse.events()
                                    .stream()
                                    .limit(
                                            3
                                    )
                                    .map(
                                            this::toSportsExternalSearchResult
                                    );
                        }
                )
                .toList();
    }

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
                item.poster_path() == null
                        ? null
                        : TMDB_IMAGE_BASE_URL
                        + item.poster_path();

        return new ExternalContentSearchResult(
                String.valueOf(
                        item.id()
                ),
                type,
                title,
                item.overview(),
                thumbnailUrl,
                releaseDate
        );
    }

    private ExternalContentSearchResult
    toSportsExternalSearchResult(
            SportsDbEventItem event
    ) {
        String thumbnailUrl =
                event.strThumb() != null
                        ? event.strThumb()
                        : event.strPoster();

        return new ExternalContentSearchResult(
                event.idEvent(),
                ContentType.SPORT,
                event.strEvent(),
                createSportsDescription(
                        event
                ),
                thumbnailUrl,
                event.dateEvent()
        );
    }

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

        String thumbnailUrl =
                item.poster_path() == null
                        ? null
                        : TMDB_IMAGE_BASE_URL
                        + item.poster_path();

        String contentUrl =
                type == ContentType.MOVIE
                        ? "https://www.themoviedb.org/movie/"
                        + externalId
                        : "https://www.themoviedb.org/tv/"
                        + externalId;

        List<String> tags =
                item.genres() == null
                        ? List.of()
                        : item.genres()
                        .stream()
                        .map(TmdbGenre::name)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(name -> !name.isBlank())
                        .distinct()
                        .toList();

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

    private Content createContentFromSportsDb(
            User creator,
            String externalId,
            String sourceType,
            SportsDbEventItem item
    ) {
        String thumbnailUrl =
                item.strThumb() != null
                        ? item.strThumb()
                        : item.strPoster();

        String contentUrl =
                "https://www.thesportsdb.com/event/"
                        + externalId;

        List<String> tags =
                new ArrayList<>();

        tags.add(
                ContentType.SPORT.name()
        );

        if (item.strSport() != null
                && !item.strSport().isBlank()) {

            tags.add(
                    item.strSport()
            );
        }

        if (item.strLeague() != null
                && !item.strLeague().isBlank()) {

            tags.add(
                    item.strLeague()
            );
        }

        return Content.createFromExternalApi(
                creator,
                ContentType.SPORT,
                item.strEvent(),
                createSportsDescription(
                        item
                ),
                thumbnailUrl,
                contentUrl,
                externalId,
                sourceType,
                tags
        );
    }

    private SportsDbEventItem getSportsEventItem(
            String externalId
    ) {
        SportsDbEventResponse response =
                sportsDbClient.getEventDetail(
                        externalId
                );

        if (response == null
                || response.events() == null
                || response.events().isEmpty()) {

            throw new IllegalArgumentException(
                    "존재하지 않는 스포츠 경기입니다."
            );
        }

        return response.events()
                .get(
                        0
                );
    }

    private String createSportsDescription(
            SportsDbEventItem item
    ) {
        List<String> descriptions =
                new ArrayList<>();

        if (item.strLeague() != null
                && !item.strLeague().isBlank()) {

            descriptions.add(
                    "리그: "
                            + item.strLeague()
            );
        }

        if (item.strSeason() != null
                && !item.strSeason().isBlank()) {

            descriptions.add(
                    "시즌: "
                            + item.strSeason()
            );
        }

        if (item.strHomeTeam() != null
                && item.strAwayTeam() != null) {

            descriptions.add(
                    "경기: "
                            + item.strHomeTeam()
                            + " vs "
                            + item.strAwayTeam()
            );
        }

        if (item.dateEvent() != null
                && !item.dateEvent().isBlank()) {

            descriptions.add(
                    "날짜: "
                            + item.dateEvent()
            );
        }

        if (item.strTime() != null
                && !item.strTime().isBlank()) {

            descriptions.add(
                    "시간: "
                            + item.strTime()
            );
        }

        if (item.strVenue() != null
                && !item.strVenue().isBlank()) {

            descriptions.add(
                    "장소: "
                            + item.strVenue()
            );
        }

        if (item.strDescriptionEN() != null
                && !item.strDescriptionEN().isBlank()) {

            descriptions.add(
                    item.strDescriptionEN()
            );
        }

        return String.join(
                "\n",
                descriptions
        );
    }

    private String getSourceType(
            ContentType type
    ) {
        return switch (type) {
            case MOVIE, TVSERIES ->
                    TMDB_SOURCE_TYPE;

            case SPORT ->
                    SPORTS_DB_SOURCE_TYPE;
        };
    }

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
                .findByEmail(
                        email
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "존재하지 않는 사용자입니다."
                                )
                );
    }

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

    private Content getContentEntity(
            UUID contentId
    ) {
        return contentRepository
                .findById(
                        contentId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "존재하지 않는 콘텐츠입니다."
                                )
                );
    }

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

        if (hasCursor
                != hasIdAfter) {

            throw new IllegalArgumentException(
                    "cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다."
            );
        }
    }

    private void validateLimit(
            int limit
    ) {
        if (limit < 1) {
            throw new IllegalArgumentException(
                    "limit은 1 이상이어야 합니다."
            );
        }
    }

    private UUID parseIdAfter(
            String idAfter
    ) {
        try {
            return UUID.fromString(
                    idAfter
            );

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "idAfter는 올바른 UUID 형식이어야 합니다.",
                    exception
            );
        }
    }


    private String getCursorValue(
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

    private void validateSortBy(
            String sortBy
    ) {
        if (!"createdAt".equals(
                sortBy
        )
                && !"watcherCount".equals(
                sortBy
        )
                && !"rate".equals(
                sortBy
        )) {

            throw new IllegalArgumentException(
                    "sortBy는 createdAt, watcherCount, rate만 사용할 수 있습니다."
            );
        }
    }

    private ContentDto toDto(
            Content content
    ) {
        return contentMapper.toDto(
                content,
                content.getAverageRating(),
                content.getReviewCount(),
                content.getWatcherCount()
        );
    }

    private ContentSummary toSummary(
            Content content
    ) {
        return contentMapper.toSummary(
                content,
                content.getAverageRating(),
                content.getReviewCount(),
                content.getWatcherCount()
        );
    }

    private ContentSummary toSummary(
            ContentQueryRow row
    ) {
        return contentMapper.toSummary(
                row.content(),
                row.averageRating(),
                row.reviewCount(),
                row.watcherCount()
        );
    }
}