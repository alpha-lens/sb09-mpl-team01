package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.TmdbClient;
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

    private static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
    private static final String TMDB_SOURCE_TYPE = "TMDB";

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;
    private final ReviewRepository reviewRepository;
    private final WatchingSessionRepository watchingSessionRepository;
    private final TmdbClient tmdbClient;

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

        if (request.type() == ContentType.SPORT) {
            throw new IllegalArgumentException("SPORT 타입은 TMDB import를 지원하지 않습니다.");
        }

        return contentRepository.findBySourceTypeAndExternalId(
                        TMDB_SOURCE_TYPE,
                        request.externalId()
                )
                .map(this::toDto)
                .orElseGet(() -> importNewExternalContent(requester, request));
    }

    private ContentDto importNewExternalContent(
            User requester,
            ContentImportRequest request
    ) {
        TmdbContentItem item = switch (request.type()) {
            case MOVIE -> tmdbClient.getMovieDetail(request.externalId());
            case TVSERIES -> tmdbClient.getTvSeriesDetail(request.externalId());
            case SPORT -> throw new IllegalArgumentException("SPORT 타입은 TMDB import를 지원하지 않습니다.");
        };

        Content content = createContentFromTmdb(
                requester,
                request.type(),
                request.externalId(),
                TMDB_SOURCE_TYPE,
                item
        );

        try {
            Content savedContent = contentRepository.saveAndFlush(content);
            return toDto(savedContent);
        } catch (DataIntegrityViolationException e) {
            Content existingContent = contentRepository.findBySourceTypeAndExternalId(
                            TMDB_SOURCE_TYPE,
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

        Sort.Direction direction = sortDirection == Direction.ASCENDING
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                0,
                limit + 1,
                Sort.by(direction, sortBy).and(Sort.by(direction, "id"))
        );

        Specification<Content> specification = createCursorSpecification(
                cursor,
                idAfter,
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
                contentRepository.count(),
                sortBy,
                sortDirection
        );
    }

    @Transactional(readOnly = true)
    public List<ExternalContentSearchResult> searchExternalContents(
            String keyword,
            ContentType type
    ) {
        TmdbSearchResponse response = switch (type) {
            case MOVIE -> tmdbClient.searchMovies(keyword);
            case TVSERIES -> tmdbClient.searchTvSeries(keyword);
            case SPORT -> throw new IllegalArgumentException("SPORT 타입은 아직 TMDB 검색을 지원하지 않습니다.");
        };

        if (response == null || response.results() == null) {
            return List.of();
        }

        return response.results().stream()
                .map(item -> toExternalSearchResult(item, type))
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

            if ("title".equals(sortBy)) {
                Predicate sortPredicate;
                Predicate sameSortValuePredicate;

                if (sortDirection == Direction.ASCENDING) {
                    sortPredicate = criteriaBuilder.greaterThan(root.get("title"), cursor);
                    sameSortValuePredicate = criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("title"), cursor),
                            criteriaBuilder.greaterThan(root.get("id"), idAfterValue)
                    );
                } else {
                    sortPredicate = criteriaBuilder.lessThan(root.get("title"), cursor);
                    sameSortValuePredicate = criteriaBuilder.and(
                            criteriaBuilder.equal(root.get("title"), cursor),
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

        if ("title".equals(sortBy)) {
            return content.getTitle();
        }

        throw new IllegalArgumentException("지원하지 않는 정렬 기준입니다.");
    }

    private void validateSortBy(String sortBy) {
        if (!"createdAt".equals(sortBy) && !"title".equals(sortBy)) {
            throw new IllegalArgumentException("sortBy는 createdAt 또는 title만 사용할 수 있습니다.");
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
}