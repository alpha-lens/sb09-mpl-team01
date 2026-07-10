package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.client.TmdbProperties;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbTeamResponse;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
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
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
        User creator = getRequester(requesterEmail);

        validateAdmin(creator);

        Content content = Content.create(
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
        User requester = getRequester(requesterEmail);

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
        Content content = switch (request.type()) {
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

                if (item == null || item.id() == null) {
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
                    contentRepository.saveAndFlush(content);

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
    public ContentDto getContent(UUID contentId) {
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
     * 콘텐츠 목록을 커서 기반으로 조회합니다.
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
        validateCursorPair(cursor, idAfter);
        validateSortBy(sortBy);

        /*
         * watcherCount와 rate는 contents 테이블의 실제 컬럼이 아니라
         * WatchingSession 및 Review 데이터를 통해 계산되는 값입니다.
         *
         * 따라서 계산값 정렬은 별도 메모리 정렬 경로로 처리합니다.
         */
        if ("watcherCount".equals(sortBy)
                || "rate".equals(sortBy)) {

            return getContentsByCalculatedSort(
                    cursor,
                    idAfter,
                    keywordLike,
                    type,
                    limit,
                    sortBy,
                    sortDirection
            );
        }

        /*
         * createdAt은 Content 엔티티의 실제 컬럼이므로
         * DB 정렬과 커서 조건을 함께 사용합니다.
         */
        Sort.Direction direction =
                sortDirection == Direction.ASCENDING
                        ? Sort.Direction.ASC
                        : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                0,
                limit + 1,
                Sort.by(direction, "createdAt")
                        .and(
                                Sort.by(
                                        direction,
                                        "id"
                                )
                        )
        );

        Specification<Content> specification =
                createContentSpecification(
                        cursor,
                        idAfter,
                        keywordLike,
                        type,
                        "createdAt",
                        sortDirection
                );

        Page<Content> contentPage =
                contentRepository.findAll(
                        specification,
                        pageable
                );

        List<Content> contents =
                contentPage.getContent();

        boolean hasNext =
                contents.size() > limit;

        List<Content> pageContents =
                hasNext
                        ? contents.subList(0, limit)
                        : contents;

        List<ContentSummary> contentSummaries =
                pageContents.stream()
                        .map(this::toSummary)
                        .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageContents.isEmpty()) {
            Content lastContent =
                    pageContents.get(
                            pageContents.size() - 1
                    );

            nextCursor =
                    getCursorValue(
                            lastContent,
                            "createdAt"
                    );

            nextIdAfter =
                    lastContent
                            .getId()
                            .toString();
        }

        return new CursorPageResponseDto<>(
                contentSummaries,
                nextCursor,
                nextIdAfter,
                hasNext,
                contentRepository.count(
                        createFilterSpecification(
                                keywordLike,
                                type
                        )
                ),
                sortBy,
                sortDirection
        );
    }

    /**
     * watcherCount, rate 계산 정렬용 목록 조회입니다.
     *
     * watcherCount와 rate는 DB 컬럼이 아니므로
     * 검색 결과를 조회한 뒤 메모리에서 정렬합니다.
     */
    private CursorPageResponseDto<ContentSummary>
    getContentsByCalculatedSort(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        Specification<Content> filterSpecification =
                createFilterSpecification(
                        keywordLike,
                        type
                );

        List<ContentSortView> sortedContents =
                contentRepository
                        .findAll(filterSpecification)
                        .stream()
                        .map(this::toContentSortView)
                        .sorted(
                                createContentSortComparator(
                                        sortBy,
                                        sortDirection
                                )
                        )
                        .toList();

        int startIndex =
                resolveStartIndex(
                        sortedContents,
                        cursor,
                        idAfter,
                        sortBy
                );

        int endIndex = Math.min(
                startIndex + limit + 1,
                sortedContents.size()
        );

        List<ContentSortView> selectedContents =
                sortedContents.subList(
                        startIndex,
                        endIndex
                );

        boolean hasNext =
                selectedContents.size() > limit;

        List<ContentSortView> pageContents =
                hasNext
                        ? selectedContents.subList(
                        0,
                        limit
                )
                        : selectedContents;

        List<ContentSummary> contentSummaries =
                pageContents.stream()
                        .map(ContentSortView::summary)
                        .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageContents.isEmpty()) {
            ContentSortView lastContent =
                    pageContents.get(
                            pageContents.size() - 1
                    );

            nextCursor =
                    getCalculatedCursorValue(
                            lastContent,
                            sortBy
                    );

            nextIdAfter =
                    lastContent.id().toString();
        }

        return new CursorPageResponseDto<>(
                contentSummaries,
                nextCursor,
                nextIdAfter,
                hasNext,
                sortedContents.size(),
                sortBy,
                sortDirection
        );
    }

    /**
     * 키워드 조건과 커서 조건을 함께 적용합니다.
     */
    private Specification<Content>
    createContentSpecification(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            String sortBy,
            Direction sortDirection
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates =
                    new ArrayList<>();

            Predicate keywordPredicate =
                    createKeywordPredicate(
                            keywordLike,
                            root,
                            criteriaBuilder
                    );

            if (keywordPredicate != null) {
                predicates.add(keywordPredicate);
            }

            if (type != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("type"),
                                type
                        )
                );
            }

            Predicate cursorPredicate =
                    createCursorPredicate(
                            cursor,
                            idAfter,
                            sortBy,
                            sortDirection,
                            root,
                            criteriaBuilder
                    );

            if (cursorPredicate != null) {
                predicates.add(cursorPredicate);
            }

            return criteriaBuilder.and(
                    predicates.toArray(
                            new Predicate[0]
                    )
            );
        };
    }

    /**
     * 키워드와 콘텐츠 타입을 함께 적용하는 Specification입니다.
     *
     * keywordLike가 null 또는 빈 문자열이면 키워드 조건을 적용하지 않습니다.
     * type이 null이면 전체 콘텐츠 타입을 조회합니다.
     */
    private Specification<Content>
    createFilterSpecification(
            String keywordLike,
            ContentType type
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates =
                    new ArrayList<>();

            Predicate keywordPredicate =
                    createKeywordPredicate(
                            keywordLike,
                            root,
                            criteriaBuilder
                    );

            if (keywordPredicate != null) {
                predicates.add(keywordPredicate);
            }

            if (type != null) {
                predicates.add(
                        criteriaBuilder.equal(
                                root.get("type"),
                                type
                        )
                );
            }

            return criteriaBuilder.and(
                    predicates.toArray(
                            new Predicate[0]
                    )
            );
        };
    }

    /**
     * 제목과 설명에서 키워드를 검색합니다.
     */
    private Predicate createKeywordPredicate(
            String keywordLike,
            jakarta.persistence.criteria.Root<Content> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        if (keywordLike == null
                || keywordLike.isBlank()) {
            return null;
        }

        String keyword =
                "%"
                        + keywordLike
                        .trim()
                        .toLowerCase()
                        + "%";

        Predicate titleLike =
                criteriaBuilder.like(
                        criteriaBuilder.lower(
                                root.get("title")
                        ),
                        keyword
                );

        Predicate descriptionLike =
                criteriaBuilder.like(
                        criteriaBuilder.lower(
                                root.get("description")
                        ),
                        keyword
                );

        return criteriaBuilder.or(
                titleLike,
                descriptionLike
        );
    }

    /**
     * createdAt 정렬용 커서 조건을 만듭니다.
     *
     * createdAt이 같은 콘텐츠가 존재할 수 있으므로
     * id를 보조 정렬 기준으로 사용합니다.
     */
    private Predicate createCursorPredicate(
            String cursor,
            String idAfter,
            String sortBy,
            Direction sortDirection,
            jakarta.persistence.criteria.Root<Content> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        if (cursor == null
                || cursor.isBlank()
                || idAfter == null
                || idAfter.isBlank()) {
            return null;
        }

        UUID idAfterValue =
                parseIdAfter(idAfter);

        if ("createdAt".equals(sortBy)) {
            Instant cursorValue =
                    parseInstantCursor(cursor);

            Predicate sortPredicate;
            Predicate sameSortValuePredicate;

            if (sortDirection
                    == Direction.ASCENDING) {

                sortPredicate =
                        criteriaBuilder.greaterThan(
                                root.get("createdAt"),
                                cursorValue
                        );

                sameSortValuePredicate =
                        criteriaBuilder.and(
                                criteriaBuilder.equal(
                                        root.get("createdAt"),
                                        cursorValue
                                ),
                                criteriaBuilder.greaterThan(
                                        root.get("id"),
                                        idAfterValue
                                )
                        );

            } else {
                sortPredicate =
                        criteriaBuilder.lessThan(
                                root.get("createdAt"),
                                cursorValue
                        );

                sameSortValuePredicate =
                        criteriaBuilder.and(
                                criteriaBuilder.equal(
                                        root.get("createdAt"),
                                        cursorValue
                                ),
                                criteriaBuilder.lessThan(
                                        root.get("id"),
                                        idAfterValue
                                )
                        );
            }

            return criteriaBuilder.or(
                    sortPredicate,
                    sameSortValuePredicate
            );
        }

        return null;
    }

    /**
     * 계산 정렬에 필요한 값들을 조회합니다.
     */
    private ContentSortView toContentSortView(
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
                        .countByContent(content);

        ContentSummary summary =
                contentMapper.toSummary(
                        content,
                        averageRating,
                        reviewCount
                );

        return new ContentSortView(
                content.getId(),
                content.getCreatedAt(),
                watcherCount,
                averageRating,
                summary
        );
    }

    /**
     * watcherCount 또는 rate 정렬 Comparator입니다.
     */
    private Comparator<ContentSortView>
    createContentSortComparator(
            String sortBy,
            Direction sortDirection
    ) {
        Comparator<ContentSortView> comparator =
                switch (sortBy) {
                    case "watcherCount" ->
                            Comparator.comparing(
                                    ContentSortView::watcherCount
                            );

                    case "rate" ->
                            Comparator.comparing(
                                    ContentSortView::averageRating
                            );

                    default ->
                            throw new IllegalArgumentException(
                                    "지원하지 않는 정렬 기준입니다."
                            );
                };

        comparator = comparator
                .thenComparing(
                        ContentSortView::createdAt
                )
                .thenComparing(
                        ContentSortView::id
                );

        if (sortDirection
                == Direction.DESCENDING) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    /**
     * 계산 정렬 결과에서 다음 페이지 시작 위치를 찾습니다.
     */
    private int resolveStartIndex(
            List<ContentSortView> sortedContents,
            String cursor,
            String idAfter,
            String sortBy
    ) {
        if (cursor == null
                || cursor.isBlank()
                || idAfter == null
                || idAfter.isBlank()) {
            return 0;
        }

        UUID idAfterValue =
                parseIdAfter(idAfter);

        for (int i = 0;
             i < sortedContents.size();
             i++) {

            ContentSortView content =
                    sortedContents.get(i);

            boolean sameCursorValue =
                    matchesCalculatedCursorValue(
                            content,
                            cursor,
                            sortBy
                    );

            boolean sameId =
                    content.id()
                            .equals(idAfterValue);

            if (sameCursorValue && sameId) {
                return i + 1;
            }
        }

        throw new IllegalArgumentException(
                "cursor와 idAfter가 현재 정렬 결과와 일치하지 않습니다."
        );
    }

    /**
     * 계산 정렬의 커서값을 비교합니다.
     */
    private boolean matchesCalculatedCursorValue(
            ContentSortView content,
            String cursor,
            String sortBy
    ) {
        return switch (sortBy) {
            case "watcherCount" ->
                    content.watcherCount()
                            .equals(
                                    parseLongCursor(
                                            cursor
                                    )
                            );

            case "rate" ->
                    Double.compare(
                            content.averageRating(),
                            parseDoubleCursor(cursor)
                    ) == 0;

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 정렬 기준입니다."
                    );
        };
    }

    private Long parseLongCursor(String cursor) {
        try {
            return Long.parseLong(cursor);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "watcherCount 정렬 시 cursor는 숫자여야 합니다."
            );
        }
    }

    private Double parseDoubleCursor(
            String cursor
    ) {
        try {
            return Double.parseDouble(cursor);

        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "rate 정렬 시 cursor는 숫자여야 합니다."
            );
        }
    }

    private String getCalculatedCursorValue(
            ContentSortView content,
            String sortBy
    ) {
        return switch (sortBy) {
            case "watcherCount" ->
                    String.valueOf(
                            content.watcherCount()
                    );

            case "rate" ->
                    String.valueOf(
                            content.averageRating()
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
            return searchSportsContents(keyword);
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
                sportsDbClient.searchTeams(keyword);

        if (teamResponse == null
                || teamResponse.teams() == null) {
            return List.of();
        }

        return teamResponse.teams()
                .stream()
                .limit(3)
                .flatMap(team -> {
                    SportsDbEventResponse eventResponse =
                            sportsDbClient
                                    .getNextEventsByTeam(
                                            team.idTeam()
                                    );

                    if (eventResponse == null
                            || eventResponse.events()
                            == null) {

                        return List
                                .<ExternalContentSearchResult>of()
                                .stream();
                    }

                    return eventResponse.events()
                            .stream()
                            .limit(3)
                            .map(
                                    this::toSportsExternalSearchResult
                            );
                })
                .toList();
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
                firstNonBlank(
                        event.strThumb(),
                        event.strPoster(),
                        event.strBanner()
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

        if (title == null || title.isBlank()) {
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

        tags.add(type.name());

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
                firstNonBlank(
                        item.strThumb(),
                        item.strPoster(),
                        item.strBanner()
                );

        String contentUrl =
                "https://www.thesportsdb.com/event/"
                        + externalId;

        List<String> tags =
                new ArrayList<>();

        tags.add(ContentType.SPORT.name());

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
                        .getEventDetail(externalId);

        if (response == null
                || response.events() == null
                || response.events().isEmpty()) {

            throw new IllegalArgumentException(
                    "존재하지 않는 스포츠 경기입니다."
            );
        }

        SportsDbEventItem item =
                response.events().get(0);

        if (item.idEvent() == null
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
        if (email == null || email.isBlank()) {
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

        if (!isOwner && !isAdmin) {
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

    private UUID parseIdAfter(
            String idAfter
    ) {
        try {
            return UUID.fromString(idAfter);

        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "idAfter는 올바른 UUID 형식이어야 합니다."
            );
        }
    }

    private Instant parseInstantCursor(
            String cursor
    ) {
        try {
            return Instant.parse(cursor);

        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "createdAt 정렬 시 cursor는 올바른 Instant 형식이어야 합니다."
            );
        }
    }

    private String getCursorValue(
            Content content,
            String sortBy
    ) {
        if ("createdAt".equals(sortBy)) {
            return content
                    .getCreatedAt()
                    .toString();
        }

        throw new IllegalArgumentException(
                "지원하지 않는 정렬 기준입니다."
        );
    }

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
                        .countByContent(content);

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount
        );
    }

    /**
     * Content 엔티티를 목록 요약 DTO로 변환합니다.
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

    /**
     * 계산 정렬에 필요한 내부 전용 데이터입니다.
     */
    private record ContentSortView(
            UUID id,
            Instant createdAt,
            Long watcherCount,
            Double averageRating,
            ContentSummary summary
    ) {
    }
}