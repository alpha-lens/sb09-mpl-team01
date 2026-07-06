package com.codeit.mpl.domain.content.service;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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

        return toDto(content);
    }

    public void deleteContent(String requesterEmail, UUID contentId) {
        User requester = getRequester(requesterEmail);
        Content content = getContentEntity(contentId);

        validateContentOwnerOrAdmin(requester, content);

        contentRepository.delete(content);
    }

    @Transactional(readOnly = true)
    public CursorPageResponseDto<ContentSummary> getContents(
            String cursor,
            String idAfter,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        validateCursorPair(cursor, idAfter);
        validateSortBy(sortBy);

        // watcherCount, rate는 Content 테이블 컬럼이 아니라 계산값입니다.
        // 따라서 현재는 메모리 정렬 경로로 분기합니다.
        if ("watcherCount".equals(sortBy) || "rate".equals(sortBy)) {
            return getContentsByCalculatedSort(
                    cursor,
                    idAfter,
                    limit,
                    sortBy,
                    sortDirection
            );
        }

        // createdAt은 실제 Content 컬럼이므로 DB 정렬 + Specification 커서를 사용합니다.
        Sort.Direction direction = sortDirection == Direction.ASCENDING
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                0,
                limit + 1,
                Sort.by(direction, "createdAt").and(Sort.by(direction, "id"))
        );

        Specification<Content> specification = createCursorSpecification(
                cursor,
                idAfter,
                "createdAt",
                sortDirection
        );

        Page<Content> contentPage = contentRepository.findAll(specification, pageable);

        List<Content> contents = contentPage.getContent();
        boolean hasNext = contents.size() > limit;

        List<Content> pageContents = hasNext
                ? contents.subList(0, limit)
                : contents;

        List<ContentSummary> contentSummaries = toSummaries(pageContents);

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageContents.isEmpty()) {
            Content lastContent = pageContents.get(pageContents.size() - 1);
            nextCursor = getCursorValue(lastContent, "createdAt");
            nextIdAfter = lastContent.getId().toString();
        }

        return new CursorPageResponseDto<>(
                contentSummaries,
                nextCursor,
                nextIdAfter,
                hasNext,
                contentRepository.count(),
                sortBy,
                sortDirection
        );
    }

    /**
     * watcherCount, rate 계산 정렬용 목록 조회입니다.
     *
     * 현재는 임시로 전체 Content를 조회한 뒤 메모리에서 정렬합니다.
     * cursor + idAfter를 함께 사용해서 이전 페이지의 마지막 항목을 찾고,
     * 그 다음 인덱스부터 다음 페이지를 구성합니다.
     */
    private CursorPageResponseDto<ContentSummary> getContentsByCalculatedSort(
            String cursor,
            String idAfter,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        List<Content> allContents = contentRepository.findAll();
        List<UUID> allContentIds = allContents.stream().map(Content::getId).toList();

        Map<UUID, ReviewStats> statsMap = new HashMap<>();
        Map<UUID, Long> watcherCountMap = new HashMap<>();

        if (!allContentIds.isEmpty()) {
            List<Object[]> reviewStats = reviewRepository.findReviewStatsByContentIds(allContentIds);
            statsMap = reviewStats.stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> new ReviewStats(
                                    row[1] != null ? (Double) row[1] : 0.0,
                                    ((Long) row[2]).intValue()
                            )
                    ));

            List<Object[]> watcherCounts = watchingSessionRepository.findWatcherCountsByContentIds(allContentIds);
            watcherCountMap = watcherCounts.stream()
                    .collect(Collectors.toMap(
                            row -> (UUID) row[0],
                            row -> (Long) row[1]
                    ));
        }

        final Map<UUID, ReviewStats> finalStatsMap = statsMap;
        final Map<UUID, Long> finalWatcherCountMap = watcherCountMap;

        List<ContentSortView> sortedContents = allContents.stream()
                .map(content -> {
                    ReviewStats stats = finalStatsMap.getOrDefault(content.getId(), new ReviewStats(0.0, 0));
                    Long watcherCount = finalWatcherCountMap.getOrDefault(content.getId(), 0L);
                    ContentSummary summary = contentMapper.toSummary(
                            content,
                            stats.averageRating(),
                            stats.reviewCount()
                    );
                    return new ContentSortView(
                            content.getId(),
                            content.getCreatedAt(),
                            watcherCount,
                            stats.averageRating(),
                            summary
                    );
                })
                .sorted(createContentSortComparator(sortBy, sortDirection))
                .toList();

        int startIndex = resolveStartIndex(
                sortedContents,
                cursor,
                idAfter,
                sortBy
        );

        int endIndex = Math.min(startIndex + limit + 1, sortedContents.size());

        List<ContentSortView> selectedContents = sortedContents.subList(startIndex, endIndex);
        boolean hasNext = selectedContents.size() > limit;

        List<ContentSortView> pageContents = hasNext
                ? selectedContents.subList(0, limit)
                : selectedContents;

        List<ContentSummary> contentSummaries = pageContents.stream()
                .map(ContentSortView::summary)
                .toList();

        String nextCursor = null;
        String nextIdAfter = null;

        if (hasNext && !pageContents.isEmpty()) {
            ContentSortView lastContent = pageContents.get(pageContents.size() - 1);
            nextCursor = getCalculatedCursorValue(lastContent, sortBy);
            nextIdAfter = lastContent.id().toString();
        }

        return new CursorPageResponseDto<>(
                contentSummaries,
                nextCursor,
                nextIdAfter,
                hasNext,
                contentRepository.count(),
                sortBy,
                sortDirection
        );
    }

    private List<ContentSummary> toSummaries(List<Content> contents) {
        if (contents.isEmpty()) {
            return List.of();
        }

        List<UUID> contentIds = contents.stream().map(Content::getId).toList();

        List<Object[]> reviewStats = reviewRepository.findReviewStatsByContentIds(contentIds);
        Map<UUID, ReviewStats> statsMap = reviewStats.stream()
                .collect(Collectors.toMap(
                        row -> (UUID) row[0],
                        row -> new ReviewStats(
                                row[1] != null ? (Double) row[1] : 0.0,
                                ((Long) row[2]).intValue()
                        )
                ));

        return contents.stream()
                .map(content -> {
                    ReviewStats stats = statsMap.getOrDefault(content.getId(), new ReviewStats(0.0, 0));
                    return contentMapper.toSummary(content, stats.averageRating(), stats.reviewCount());
                })
                .toList();
    }

    /**
     * watcherCount 또는 rate 기준 Comparator를 만듭니다.
     *
     * 같은 정렬값이 나올 수 있으므로 createdAt, id를 보조 정렬 기준으로 사용합니다.
     */
    private Comparator<ContentSortView> createContentSortComparator(
            String sortBy,
            Direction sortDirection
    ) {
        Comparator<ContentSortView> comparator = switch (sortBy) {
            case "watcherCount" -> Comparator.comparing(ContentSortView::watcherCount);
            case "rate" -> Comparator.comparing(ContentSortView::averageRating);
            default -> throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다.");
        };

        comparator = comparator
                .thenComparing(ContentSortView::createdAt)
                .thenComparing(ContentSortView::id);

        if (sortDirection == Direction.DESCENDING) {
            comparator = comparator.reversed();
        }

        return comparator;
    }

    /**
     * 계산 정렬용 cursor 시작 위치를 계산합니다.
     *
     * cursor: 이전 응답의 nextCursor
     * idAfter: 이전 응답의 nextIdAfter
     *
     * 둘 다 없으면 첫 페이지입니다.
     * 둘 다 있으면 정렬값과 id가 모두 일치하는 항목을 찾고, 그 다음부터 반환합니다.
     */
    private int resolveStartIndex(
            List<ContentSortView> sortedContents,
            String cursor,
            String idAfter,
            String sortBy
    ) {
        if (cursor == null || cursor.isBlank()
                || idAfter == null || idAfter.isBlank()) {
            return 0;
        }

        UUID idAfterValue = parseIdAfter(idAfter);

        for (int i = 0; i < sortedContents.size(); i++) {
            ContentSortView content = sortedContents.get(i);

            boolean sameCursorValue = matchesCalculatedCursorValue(
                    content,
                    cursor,
                    sortBy
            );

            boolean sameId = content.id().equals(idAfterValue);

            if (sameCursorValue && sameId) {
                return i + 1;
            }
        }

        // 첫 페이지로 되돌리면 중복 페이지가 발생하므로 명시적으로 잘못된 요청 처리
        throw new IllegalArgumentException("cursor와 idAfter가 현재 정렬 결과와 일치하지 않습니다.");
    }

    /**
     * cursor 문자열을 실제 계산 정렬값과 비교합니다.
     */
    private boolean matchesCalculatedCursorValue(
            ContentSortView content,
            String cursor,
            String sortBy
    ) {
        return switch (sortBy) {
            case "watcherCount" ->
                    content.watcherCount().equals(parseLongCursor(cursor));
            case "rate" ->
                    Double.compare(content.averageRating(), parseDoubleCursor(cursor)) == 0;
            default ->
                    throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다.");
        };
    }

    private Long parseLongCursor(String cursor) {
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("watcherCount 정렬 시 cursor는 숫자여야 합니다.");
        }
    }

    private Double parseDoubleCursor(String cursor) {
        try {
            return Double.parseDouble(cursor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("rate 정렬 시 cursor는 숫자여야 합니다.");
        }
    }

    private String getCalculatedCursorValue(
            ContentSortView content,
            String sortBy
    ) {
        return switch (sortBy) {
            case "watcherCount" -> String.valueOf(content.watcherCount());
            case "rate" -> String.valueOf(content.averageRating());
            default -> throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다.");
        };
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

    private Specification<Content> createCursorSpecification(
            String cursor,
            String idAfter,
            String sortBy,
            Direction sortDirection
    ) {
        return (root, query, criteriaBuilder) -> {
            if (cursor == null || cursor.isBlank() || idAfter == null || idAfter.isBlank()) {
                return criteriaBuilder.conjunction();
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

            return criteriaBuilder.conjunction();
        };
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
        Double averageRating = reviewRepository.findAverageRatingByContent(content);

        if (averageRating == null) {
            averageRating = 0.0;
        }

        Integer reviewCount = Math.toIntExact(reviewRepository.countByContent(content));
        Long watcherCount = watchingSessionRepository.countByContent(content);

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount
        );
    }

    private ContentSummary toSummary(Content content) {
        Double averageRating = reviewRepository.findAverageRatingByContent(content);

        if (averageRating == null) {
            averageRating = 0.0;
        }

        Integer reviewCount = Math.toIntExact(reviewRepository.countByContent(content));

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