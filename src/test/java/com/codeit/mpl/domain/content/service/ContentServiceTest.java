package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.WatchingSessionRepository;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ContentMapper contentMapper;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private WatchingSessionRepository watchingSessionRepository;
    @Mock
    private TmdbClient tmdbClient;
    @Mock
    private SportsDbClient sportsDbClient;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ContentSearchRepository contentSearchRepository;

    @InjectMocks
    private ContentService contentService;

    @Test
    @DisplayName("getContents queries and maps stats successfully")
    void testGetContentsQueries() {
        // Arrange
        UUID contentId = UUID.randomUUID();
        Content mockContent = mock(Content.class);
        lenient().when(mockContent.getId()).thenReturn(contentId);
        lenient().when(mockContent.getCreatedAt()).thenReturn(Instant.now());

        PageImpl<Content> contentPage = new PageImpl<>(List.of(mockContent));
        when(contentRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(contentPage);

        when(reviewRepository.findAverageRatingByContent(mockContent)).thenReturn(4.5);
        when(reviewRepository.countByContent(mockContent)).thenReturn(10L);

        ContentSummary mockSummary = new ContentSummary(contentId, ContentType.MOVIE, "Title", "Desc", "thumb", List.of("tag"), 4.5, 10);
        when(contentMapper.toSummary(mockContent, 4.5, 10)).thenReturn(mockSummary);

        // Act
        CursorPageResponseDto<ContentSummary> result = contentService.getContents(
                null, null, null, null, 20, "createdAt", Direction.DESCENDING
        );

        // Assert
        assertThat(result.data()).hasSize(1);
        assertThat(result.data().get(0).averageRating()).isEqualTo(4.5);
        assertThat(result.data().get(0).reviewCount()).isEqualTo(10);
        verify(reviewRepository, times(1)).findAverageRatingByContent(mockContent);
        verify(reviewRepository, times(1)).countByContent(mockContent);
    }

    @Test
    @DisplayName("getContentsByCalculatedSort queries stats and watcher counts")
    void testGetContentsByCalculatedSortQueries() {
        // Arrange
        UUID contentId = UUID.randomUUID();
        Content mockContent = mock(Content.class);
        lenient().when(mockContent.getId()).thenReturn(contentId);
        lenient().when(mockContent.getCreatedAt()).thenReturn(Instant.now());

        when(contentRepository.findAll(any(Specification.class))).thenReturn(List.of(mockContent));

        com.codeit.mpl.domain.review.dto.response.ReviewStats mockStats = new com.codeit.mpl.domain.review.dto.response.ReviewStats(contentId, 4.0, 5L);
        when(reviewRepository.findReviewStatsByContentIds(anyList())).thenReturn(List.of(mockStats));
        
        Object[] watcherCountRow = new Object[]{contentId, 15L};
        when(watchingSessionRepository.findWatcherCountsByContentIds(anyList())).thenReturn(java.util.Collections.singletonList(watcherCountRow));

        ContentSummary mockSummary = new ContentSummary(contentId, ContentType.MOVIE, "Title", "Desc", "thumb", List.of("tag"), 4.0, 5);
        when(contentMapper.toSummary(mockContent, 4.0, 5)).thenReturn(mockSummary);

        // Act
        CursorPageResponseDto<ContentSummary> result = contentService.getContents(
                null, null, null, null, 20, "watcherCount", Direction.DESCENDING
        );

        // Assert
        assertThat(result.data()).hasSize(1);
        verify(reviewRepository, times(1)).findReviewStatsByContentIds(anyList());
        verify(watchingSessionRepository, times(1)).findWatcherCountsByContentIds(anyList());
    }
}
