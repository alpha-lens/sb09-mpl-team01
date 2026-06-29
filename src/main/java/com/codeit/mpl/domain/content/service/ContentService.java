package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;

    public ContentDto createContent(String requesterEmail, ContentCreateRequest request) {
        User creator = getRequester(requesterEmail);

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

    private User getRequester(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
        }

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));
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
        /*
         * TODO (Review 파트 구현 후 연결)
         *
         * ReviewRepository에 아래 메서드가 추가되면 실제 집계값으로 교체한다.
         *
         * Double averageRating = reviewRepository.findAverageRatingByContent(content);
         * Integer reviewCount = Math.toIntExact(reviewRepository.countByContent(content));
         */
        Double averageRating = 0.0;
        Integer reviewCount = 0;

        /*
         * TODO (WatchingSession 파트 구현 후 연결)
         *
         * WatchingSessionRepository에 아래 메서드가 추가되면 실제 시청자 수로 교체한다.
         *
         * Long watcherCount = watchingSessionRepository.countByContent(content);
         */
        Long watcherCount = 0L;

        return contentMapper.toDto(
                content,
                averageRating,
                reviewCount,
                watcherCount
        );
    }

    private ContentSummary toSummary(Content content) {
        /*
         * TODO (Review 파트 구현 후 연결)
         *
         * ReviewRepository에 아래 메서드가 추가되면 실제 집계값으로 교체한다.
         *
         * Double averageRating = reviewRepository.findAverageRatingByContent(content);
         * Integer reviewCount = Math.toIntExact(reviewRepository.countByContent(content));
         */
        Double averageRating = 0.0;
        Integer reviewCount = 0;

        return contentMapper.toSummary(
                content,
                averageRating,
                reviewCount
        );
    }
}