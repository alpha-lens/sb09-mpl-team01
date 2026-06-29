package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContentService {

    private final ContentRepository contentRepository;
    private final UserRepository userRepository;
    private final ContentMapper contentMapper;

    public ContentDto createContent(UUID creatorId, ContentCreateRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

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

    public ContentDto updateContent(UUID contentId, ContentUpdateRequest request) {
        Content content = getContentEntity(contentId);

        content.update(
                request.title(),
                request.description(),
                request.tags()
        );

        return toDto(content);
    }

    public void deleteContent(UUID contentId) {
        Content content = getContentEntity(contentId);

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
        Sort.Direction direction = sortDirection == Direction.ASCENDING
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(
                0,
                limit,
                Sort.by(direction, sortBy)
        );

        Page<Content> contentPage = contentRepository.findAll(pageable);

        List<ContentSummary> contentSummaries = contentPage.getContent().stream()
                .map(this::toSummary)
                .toList();

        return new CursorPageResponseDto<>(
                contentSummaries,
                null,
                null,
                contentPage.hasNext(),
                contentPage.getTotalElements(),
                sortBy,
                sortDirection
        );
    }

    private Content getContentEntity(UUID contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));
    }

    private ContentDto toDto(Content content) {
        /*
         * TODO (Review 파트 구현 후 연결)
         *
         * 현재 averageRating, reviewCount는 리뷰 파트의 집계 메서드가 아직 확정되지 않아
         * 임시값으로 0.0, 0을 반환한다.
         *
         * ReviewRepository에 아래 메서드가 추가되면,
         * ContentService에 ReviewRepository를 주입하고 아래 코드로 교체한다.
         *
         * 필요한 메서드 예시:
         * - reviewRepository.findAverageRatingByContent(content)
         * - reviewRepository.countByContent(content)
         *
         * 교체 예정 코드:
         * Double averageRating = reviewRepository.findAverageRatingByContent(content);
         * Integer reviewCount = Math.toIntExact(reviewRepository.countByContent(content));
         */
        Double averageRating = 0.0;
        Integer reviewCount = 0;

        /*
         * TODO (WatchingSession 파트 구현 후 연결)
         *
         * 현재 watcherCount는 실시간 같이보기 파트의 WatchingSessionRepository가 아직 확정되지 않아
         * 임시값으로 0L을 반환한다.
         *
         * WatchingSessionRepository에 아래 메서드가 추가되면,
         * ContentService에 WatchingSessionRepository를 주입하고 아래 코드로 교체한다.
         *
         * 필요한 메서드 예시:
         * - watchingSessionRepository.countByContent(content)
         *
         * 교체 예정 코드:
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
         * 현재 averageRating, reviewCount는 리뷰 파트의 집계 메서드가 아직 확정되지 않아
         * 임시값으로 0.0, 0을 반환한다.
         *
         * ReviewRepository에 아래 메서드가 추가되면,
         * ContentService에 ReviewRepository를 주입하고 아래 코드로 교체한다.
         *
         * 필요한 메서드 예시:
         * - reviewRepository.findAverageRatingByContent(content)
         * - reviewRepository.countByContent(content)
         *
         * 교체 예정 코드:
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