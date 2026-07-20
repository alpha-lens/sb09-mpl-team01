package com.codeit.mpl.domain.content.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
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
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.WatchingSessionRepository;
import com.codeit.mpl.domain.review.dto.response.ReviewStats;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.event.ContentEvent;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ContentService {

    private static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    private static final String TMDB_SOURCE_TYPE = "TMDB";
    private static final String SPORTS_DB_SOURCE_TYPE = "THE_SPORTS_DB";

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;
    private final ReviewRepository reviewRepository;
    private final WatchingSessionRepository watchingSessionRepository;
    private final TmdbClient tmdbClient;
    private final SportsDbClient sportsDbClient;
    private final ApplicationEventPublisher eventPublisher;
    private final ContentSearchRepository contentSearchRepository;
    private final co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    public ContentDto createContent(String requesterEmail, ContentCreateRequest request) {
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

        Content savedContent = contentRepository.save(content);
        eventPublisher.publishEvent(new ContentEvent(savedContent, ContentEvent.EventType.CREATED));
        return toDto(savedContent);
    }

    public ContentDto importExternalContent(
            String requesterEmail,
            ContentImportRequest request
    ) {
        User requester = getRequester(requesterEmail);
        validateAdmin(requester);

        String sourceType = getSourceType(request.type());

        return contentRepository.findBySourceTypeAndExternalId(
                        sourceType,
                        request.externalId()
                )
                .map(this::toDto)
                .orElseGet(() -> importNewExternalContent(requester, request, sourceType));
    }

    private ContentDto importNewExternalContent(
            User requester,
            ContentImportRequest request,
            String sourceType
    ) {
        Content content = switch (request.type()) {
            case MOVIE, TVSERIES -> {
                TmdbContentItem item = switch (request.type()) {
                    case MOVIE -> tmdbClient.getMovieDetail(request.externalId());
                    case TVSERIES -> tmdbClient.getTvSeriesDetail(request.externalId());
                    case SPORT -> throw new IllegalArgumentException("SPORT 타입은 TMDB import를 지원하지 않습니다.");
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
                SportsDbEventItem item = getSportsEventItem(request.externalId());

                yield createContentFromSportsDb(
                        requester,
                        request.externalId(),
                        sourceType,
                        item
                );
            }
        };

        try {
            Content savedContent = contentRepository.saveAndFlush(content);
            eventPublisher.publishEvent(new ContentEvent(savedContent, ContentEvent.EventType.CREATED));
            return toDto(savedContent);
        } catch (DataIntegrityViolationException e) {
            Content existingContent = contentRepository.findBySourceTypeAndExternalId(
                            sourceType,
                            request.externalId()
                    )
                    .orElseThrow(() -> e);

            return toDto(existingContent);
        }
    }

    @Transactional(readOnly = true)
    public ContentDto getContent(UUID contentId) {
        Content content = getContentEntity(contentId);
        return toDto(content);
    }

    public ContentDto updateContent(
            String requesterEmail,
            UUID contentId,
            ContentUpdateRequest request
    ) {
        User requester = getRequester(requesterEmail);
        Content content = getContentEntity(contentId);

        validateContentOwnerOrAdmin(requester, content);

        content.update(
                request.title(),
                request.description(),
                request.tags()
        );
        eventPublisher.publishEvent(new ContentEvent(content, ContentEvent.EventType.UPDATED));

        return toDto(content);
    }

    public void deleteContent(String requesterEmail, UUID contentId) {
        User requester = getRequester(requesterEmail);
        Content content = getContentEntity(contentId);

        validateContentOwnerOrAdmin(requester, content);

        contentRepository.delete(content);
        eventPublisher.publishEvent(new ContentEvent(content, ContentEvent.EventType.DELETED));
    }

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

        // 검색어가 포함된 경우 Elasticsearch를 활용한 전문/초성 검색 수행
        if (keywordLike != null && !keywordLike.isBlank()) {
            try {
                return getContentsViaElasticsearch(cursor, idAfter, keywordLike, type, limit, sortBy, sortDirection);
            } catch (Exception e) {
                log.error("Elasticsearch search failed, falling back to database search", e);
                // ES 실패 시 기존 DB 검색으로 폴백
            }
        }

        Sort.Direction direction = sortDirection == Direction.ASCENDING
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String dbSortBy = "rate".equals(sortBy) ? "averageRating" : sortBy;

        Pageable pageable = PageRequest.of(
                0,
                limit + 1,
                Sort.by(direction, dbSortBy).and(Sort.by(direction, "id"))
        );

        // 검색 조건(keywordLike) + 커서 조건(cursor, idAfter)을 함께 적용합니다.
        Specification<Content> specification = createContentSpecification(
                cursor,
                idAfter,
                keywordLike,
                type,
                sortBy,
                sortDirection
        );

        Page<Content> contentPage = contentRepository.findAll(specification, pageable);

        List<Content> contents = contentPage.getContent();
        boolean hasNext = contents.size() > limit;

        List<Content> pageContents = hasNext
                ? contents.subList(0, limit)
                : contents;

        List<ContentSummary> contentSummaries = pageContents.stream()
                .map(this::toSummary)
                .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageContents.isEmpty()) {
            Content lastContent = pageContents.get(pageContents.size() - 1);
            nextCursor = getCursorValue(lastContent, sortBy);
            nextIdAfter = lastContent.getId().toString();
        }

        return new CursorPageResponseDto<>(
                contentSummaries,
                nextCursor,
                nextIdAfter,
                hasNext,
                contentRepository.count(createKeywordAndTypeSpecification(keywordLike, type)),
                sortBy,
                sortDirection
        );
    }

    private CursorPageResponseDto<ContentSummary> getContentsViaElasticsearch(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        boolean isChosung = keywordLike.trim().matches("^[ㄱ-ㅎ\\s]+$");
        Pageable pageable = PageRequest.of(0, 5000);

        List<ContentDocument> docs;
        if (isChosung) {
            String chosungKeyword = keywordLike.trim().replaceAll("\\s+", "");
            docs = contentSearchRepository.searchByChosung(chosungKeyword, pageable).getContent();
        } else {
            String trimmedKeyword = keywordLike.trim();
            if (trimmedKeyword.contains(" ")) {
                List<String> tokens = analyzeKeywordWithNori(trimmedKeyword);
                if (tokens.size() > 1) {
                    docs = searchByMultiToken(tokens, pageable);
                } else {
                    docs = contentSearchRepository.searchByKeyword(trimmedKeyword, pageable).getContent();
                }
            } else {
                docs = contentSearchRepository.searchByKeyword(trimmedKeyword, pageable).getContent();
            }
        }

        List<UUID> matchingIds = docs.stream()
                .map(doc -> UUID.fromString(doc.getId()))
                .toList();

        if (matchingIds.isEmpty()) {
            return new CursorPageResponseDto<>(
                    List.of(),
                    null,
                    null,
                    false,
                    0,
                    sortBy,
                    sortDirection
            );
        }

        // ES로 찾은 ID 목록 내에서 타입, 검색어, 커서 조건을 만족하고 정렬된 페이지를 DB 쿼리로 조회
        Specification<Content> specification = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(root.get("id").in(matchingIds));
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            Predicate cursorPredicate = createCursorPredicate(cursor, idAfter, sortBy, sortDirection, root, criteriaBuilder);
            if (cursorPredicate != null) {
                predicates.add(cursorPredicate);
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Sort.Direction direction = sortDirection == Direction.ASCENDING
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        String dbSortBy = "rate".equals(sortBy) ? "averageRating" : sortBy;

        Pageable dbPageable = PageRequest.of(
                0,
                limit + 1,
                Sort.by(direction, dbSortBy).and(Sort.by(direction, "id"))
        );

        Page<Content> contentPage = contentRepository.findAll(specification, dbPageable);
        List<Content> contents = contentPage.getContent();
        boolean hasNext = contents.size() > limit;

        List<Content> pageContents = hasNext
                ? contents.subList(0, limit)
                : contents;

        List<ContentSummary> contentSummaries = pageContents.stream()
                .map(this::toSummary)
                .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageContents.isEmpty()) {
            Content lastContent = pageContents.get(pageContents.size() - 1);
            nextCursor = getCursorValue(lastContent, sortBy);
            nextIdAfter = lastContent.getId().toString();
        }

        // 전체 카운트는 매칭된 ID 리스트 중 Type이 일치하는 항목 수
        long totalCount = matchingIds.size();
        if (type != null) {
            totalCount = contentRepository.count((root, query, criteriaBuilder) ->
                criteriaBuilder.and(
                        root.get("id").in(matchingIds),
                        criteriaBuilder.equal(root.get("type"), type)
                )
            );
        }

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
     * 검색 조건과 커서 조건을 동시에 적용하는 Specification입니다.
     *
     * keywordLike:
     * - null 또는 blank이면 검색 조건을 적용하지 않습니다.
     * - title, description에 대해 대소문자 구분 없이 LIKE 검색합니다.
     *
     * cursor + idAfter:
     * - 둘 다 없으면 첫 페이지입니다.
     * - 둘 다 있으면 이전 페이지의 마지막 데이터 이후부터 조회합니다.
     */
    private Specification<Content> createContentSpecification(
            String cursor,
            String idAfter,
            String keywordLike,
            ContentType type,
            String sortBy,
            Direction sortDirection
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            Predicate keywordPredicate = createKeywordPredicate(
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

            Predicate cursorPredicate = createCursorPredicate(
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

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * keywordLike 검색 및 타입 필터 전용 Specification입니다.
     *
     * totalCount 계산과 계산 정렬 경로에서 재사용합니다.
     */
    private Specification<Content> createKeywordAndTypeSpecification(String keywordLike, ContentType type) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            Predicate keywordPredicate = createKeywordPredicate(
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

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * title, description 기준 검색 Predicate를 만듭니다.
     *
     * 현재는 Content 엔티티의 기본 문자열 컬럼만 대상으로 검색합니다.
     * tags까지 검색하려면 Content 엔티티의 tags 매핑 구조에 맞춰 join 조건을 추가해야 합니다.
     */
    private Predicate createKeywordPredicate(
            String keywordLike,
            jakarta.persistence.criteria.Root<Content> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        if (keywordLike == null || keywordLike.isBlank()) {
            return null;
        }

        String keyword = "%" + keywordLike.trim().toLowerCase() + "%";

        Predicate titleLike = criteriaBuilder.like(
                criteriaBuilder.lower(root.get("title")),
                keyword
        );

        Predicate descriptionLike = criteriaBuilder.like(
                criteriaBuilder.lower(root.get("description")),
                keyword
        );

        return criteriaBuilder.or(titleLike, descriptionLike);
    }

    /**
     * 정렬 기준별 cursor Predicate를 만듭니다.
     * 같은 정렬 값이 있을 수 있으므로 id를 보조 기준으로 사용합니다.
     */
    private Predicate createCursorPredicate(
            String cursor,
            String idAfter,
            String sortBy,
            Direction sortDirection,
            jakarta.persistence.criteria.Root<Content> root,
            jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder
    ) {
        if (cursor == null || cursor.isBlank() || idAfter == null || idAfter.isBlank()) {
            return null;
        }

        UUID idAfterValue = parseIdAfter(idAfter);

        if ("createdAt".equals(sortBy)) {
            Instant cursorValue = parseInstantCursor(cursor);

            Predicate sortPredicate;
            Predicate sameSortValuePredicate;

            if (sortDirection == Direction.ASCENDING) {
                sortPredicate = criteriaBuilder.greaterThan(root.get("createdAt"), cursorValue);
                sameSortValuePredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("createdAt"), cursorValue),
                        criteriaBuilder.greaterThan(root.get("id"), idAfterValue)
                );
            } else {
                sortPredicate = criteriaBuilder.lessThan(root.get("createdAt"), cursorValue);
                sameSortValuePredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("createdAt"), cursorValue),
                        criteriaBuilder.lessThan(root.get("id"), idAfterValue)
                );
            }

            return criteriaBuilder.or(sortPredicate, sameSortValuePredicate);
        }

        if ("watcherCount".equals(sortBy)) {
            Long cursorValue = Long.parseLong(cursor);

            Predicate sortPredicate;
            Predicate sameSortValuePredicate;

            if (sortDirection == Direction.ASCENDING) {
                sortPredicate = criteriaBuilder.greaterThan(root.get("watcherCount"), cursorValue);
                sameSortValuePredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("watcherCount"), cursorValue),
                        criteriaBuilder.greaterThan(root.get("id"), idAfterValue)
                );
            } else {
                sortPredicate = criteriaBuilder.lessThan(root.get("watcherCount"), cursorValue);
                sameSortValuePredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("watcherCount"), cursorValue),
                        criteriaBuilder.lessThan(root.get("id"), idAfterValue)
                );
            }

            return criteriaBuilder.or(sortPredicate, sameSortValuePredicate);
        }

        if ("rate".equals(sortBy)) {
            Double cursorValue = Double.parseDouble(cursor);

            Predicate sortPredicate;
            Predicate sameSortValuePredicate;

            if (sortDirection == Direction.ASCENDING) {
                sortPredicate = criteriaBuilder.greaterThan(root.get("averageRating"), cursorValue);
                sameSortValuePredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("averageRating"), cursorValue),
                        criteriaBuilder.greaterThan(root.get("id"), idAfterValue)
                );
            } else {
                sortPredicate = criteriaBuilder.lessThan(root.get("averageRating"), cursorValue);
                sameSortValuePredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(root.get("averageRating"), cursorValue),
                        criteriaBuilder.lessThan(root.get("id"), idAfterValue)
                );
            }

            return criteriaBuilder.or(sortPredicate, sameSortValuePredicate);
        }

        return null;
    }


    @Transactional(readOnly = true)
    public List<ExternalContentSearchResult> searchExternalContents(
            String keyword,
            ContentType type
    ) {
        if (type == ContentType.SPORT) {
            return searchSportsContents(keyword);
        }

        TmdbSearchResponse response = switch (type) {
            case MOVIE -> tmdbClient.searchMovies(keyword);
            case TVSERIES -> tmdbClient.searchTvSeries(keyword);
            case SPORT -> throw new IllegalArgumentException("SPORT 타입은 SportsDB 검색을 사용해야 합니다.");
        };

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .map(item -> toExternalSearchResult(item, type))
                .toList();
    }

    private List<ExternalContentSearchResult> searchSportsContents(String keyword) {
        SportsDbTeamResponse teamResponse = sportsDbClient.searchTeams(keyword);

        if (teamResponse == null || teamResponse.teams() == null) {
            return List.of();
        }

        return teamResponse.teams().stream()
                .limit(3)
                .flatMap(team -> {
                    SportsDbEventResponse eventResponse =
                            sportsDbClient.getNextEventsByTeam(team.idTeam());

                    if (eventResponse == null || eventResponse.events() == null) {
                        return List.<ExternalContentSearchResult>of().stream();
                    }

                    return eventResponse.events().stream()
                            .limit(3)
                            .map(this::toSportsExternalSearchResult);
                })
                .toList();
    }

    private ExternalContentSearchResult toExternalSearchResult(
            TmdbContentItem item,
            ContentType type
    ) {
        String title = type == ContentType.MOVIE
                ? item.title()
                : item.name();

        String releaseDate = type == ContentType.MOVIE
                ? item.release_date()
                : item.first_air_date();

        String thumbnailUrl = item.poster_path() == null
                ? null
                : TMDB_IMAGE_BASE_URL + item.poster_path();

        return new ExternalContentSearchResult(
                String.valueOf(item.id()),
                type,
                title,
                item.overview(),
                thumbnailUrl,
                releaseDate
        );
    }

    private ExternalContentSearchResult toSportsExternalSearchResult(SportsDbEventItem event) {
        String thumbnailUrl = event.strThumb() != null
                ? event.strThumb()
                : event.strPoster();

        return new ExternalContentSearchResult(
                event.idEvent(),
                ContentType.SPORT,
                event.strEvent(),
                createSportsDescription(event),
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
        String title = type == ContentType.MOVIE
                ? item.title()
                : item.name();

        String thumbnailUrl = item.poster_path() == null
                ? null
                : TMDB_IMAGE_BASE_URL + item.poster_path();

        String contentUrl = type == ContentType.MOVIE
                ? "https://www.themoviedb.org/movie/" + externalId
                : "https://www.themoviedb.org/tv/" + externalId;

        List<String> tags = new ArrayList<>();
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

    private Content createContentFromSportsDb(
            User creator,
            String externalId,
            String sourceType,
            SportsDbEventItem item
    ) {
        String thumbnailUrl = item.strThumb() != null
                ? item.strThumb()
                : item.strPoster();

        String contentUrl = "https://www.thesportsdb.com/event/" + externalId;

        List<String> tags = new ArrayList<>();
        tags.add(ContentType.SPORT.name());

        if (item.strSport() != null && !item.strSport().isBlank()) {
            tags.add(item.strSport());
        }

        if (item.strLeague() != null && !item.strLeague().isBlank()) {
            tags.add(item.strLeague());
        }

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

    private SportsDbEventItem getSportsEventItem(String externalId) {
        SportsDbEventResponse response = sportsDbClient.getEventDetail(externalId);

        if (response == null || response.events() == null || response.events().isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 스포츠 경기입니다.");
        }

        return response.events().get(0);
    }

    private String createSportsDescription(SportsDbEventItem item) {
        List<String> descriptions = new ArrayList<>();

        if (item.strLeague() != null && !item.strLeague().isBlank()) {
            descriptions.add("리그: " + item.strLeague());
        }

        if (item.strSeason() != null && !item.strSeason().isBlank()) {
            descriptions.add("시즌: " + item.strSeason());
        }

        if (item.strHomeTeam() != null && item.strAwayTeam() != null) {
            descriptions.add("경기: " + item.strHomeTeam() + " vs " + item.strAwayTeam());
        }

        if (item.dateEvent() != null && !item.dateEvent().isBlank()) {
            descriptions.add("날짜: " + item.dateEvent());
        }

        if (item.strTime() != null && !item.strTime().isBlank()) {
            descriptions.add("시간: " + item.strTime());
        }

        if (item.strVenue() != null && !item.strVenue().isBlank()) {
            descriptions.add("장소: " + item.strVenue());
        }

        if (item.strDescriptionEN() != null && !item.strDescriptionEN().isBlank()) {
            descriptions.add(item.strDescriptionEN());
        }

        return String.join("\n", descriptions);
    }

    private String getSourceType(ContentType type) {
        return switch (type) {
            case MOVIE, TVSERIES -> TMDB_SOURCE_TYPE;
            case SPORT -> SPORTS_DB_SOURCE_TYPE;
        };
    }

    private User getRequester(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
    }

    private void validateAdmin(User requester) {
        if (requester.getRole() != UserRole.ADMIN) {
            throw new IllegalArgumentException("관리자만 콘텐츠를 등록할 수 있습니다.");
        }
    }

    private void validateContentOwnerOrAdmin(User requester, Content content) {
        boolean isOwner = content.getCreator().getId().equals(requester.getId());
        boolean isAdmin = requester.getRole() == UserRole.ADMIN;

        if (!isOwner && !isAdmin) {
            throw new IllegalArgumentException("콘텐츠를 수정하거나 삭제할 권한이 없습니다.");
        }
    }

    private Content getContentEntity(UUID contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
    }

    private void validateCursorPair(String cursor, String idAfter) {
        boolean hasCursor = cursor != null && !cursor.isBlank();
        boolean hasIdAfter = idAfter != null && !idAfter.isBlank();

        if (hasCursor != hasIdAfter) {
            throw new IllegalArgumentException("cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다.");
        }
    }

    private UUID parseIdAfter(String idAfter) {
        try {
            return UUID.fromString(idAfter);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("idAfter는 올바른 UUID 형식이어야 합니다.");
        }
    }

    private Instant parseInstantCursor(String cursor) {
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("createdAt 정렬 시 cursor는 올바른 Instant 형식이어야 합니다.");
        }
    }

    private String getCursorValue(Content content, String sortBy) {
        if ("createdAt".equals(sortBy)) {
            return content.getCreatedAt().toString();
        }
        if ("watcherCount".equals(sortBy)) {
            return String.valueOf(content.getWatcherCount());
        }
        if ("rate".equals(sortBy)) {
            return String.valueOf(content.getAverageRating());
        }

        throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다.");
    }

    private void validateSortBy(String sortBy) {
        if (!"createdAt".equals(sortBy)
                && !"watcherCount".equals(sortBy)
                && !"rate".equals(sortBy)) {
            throw new IllegalArgumentException("sortBy는 createdAt, watcherCount, rate만 사용할 수 있습니다.");
        }
    }

    private ContentDto toDto(Content content) {
        return contentMapper.toDto(
                content,
                content.getAverageRating(),
                content.getReviewCount(),
                content.getWatcherCount()
        );
    }

    private ContentSummary toSummary(Content content) {
        return contentMapper.toSummary(
                content,
                content.getAverageRating(),
                content.getReviewCount()
        );
    }

    private List<String> analyzeKeywordWithNori(String keyword) {
        if (elasticsearchClient == null) {
            return List.of(keyword);
        }
        try {
            AnalyzeResponse response = elasticsearchClient.indices().analyze(a -> a
                    .index("contents")
                    .analyzer("nori_analyzer")
                    .text(keyword)
            );
            List<String> tokens = response.tokens().stream()
                    .map(AnalyzeToken::token)
                    .filter(Objects::nonNull)
                    .filter(t -> !t.isBlank())
                    .toList();
            return tokens.isEmpty() ? List.of(keyword) : tokens;
        } catch (Exception e) {
            log.warn("Nori tokenization failed for keyword: {}, falling back to raw keyword. Reason: {}", keyword, e.getMessage());
            return List.of(keyword);
        }
    }

    private List<ContentDocument> searchByMultiToken(List<String> tokens, Pageable pageable) {
        try {
            BoolQuery.Builder boolBuilder = new co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery.Builder();

            for (String token : tokens) {
                Query titleMatch = Query.of(q -> q.match(m -> m.field("title").query(token).fuzziness("AUTO").boost(3.0f)));
                Query titleAutoMatch = Query.of(q -> q.match(m -> m.field("title.autocomplete").query(token).boost(2.5f)));
                Query tagsMatch = Query.of(q -> q.match(m -> m.field("tags").query(token).fuzziness("AUTO").boost(2.0f)));
                Query tagsAutoMatch = Query.of(q -> q.match(m -> m.field("tags.autocomplete").query(token).boost(1.5f)));
                Query descMatch = Query.of(q -> q.match(m -> m.field("description").query(token).fuzziness("AUTO")));

                Query tokenQuery = co.elastic.clients.elasticsearch._types.query_dsl.Query.of(q -> q.bool(b -> b.should(titleMatch, titleAutoMatch, tagsMatch, tagsAutoMatch, descMatch)));
                boolBuilder.must(tokenQuery);
            }

            var searchResponse = elasticsearchClient.search(s -> s
                            .index("contents")
                            .query(q -> q.bool(boolBuilder.build()))
                            .size(pageable.getPageSize()),
                    ContentDocument.class
            );

            return searchResponse.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (Exception e) {
            log.error("Multi-token search failed, fallback to repository searchByKeyword", e);
            String joined = String.join(" ", tokens);
            return contentSearchRepository.searchByKeyword(joined, pageable).getContent();
        }
    }
}
