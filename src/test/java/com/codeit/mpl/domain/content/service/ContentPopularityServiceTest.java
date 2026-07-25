package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.repository.ContentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentPopularityService 단위 테스트")
class ContentPopularityServiceTest {

    @Mock
    private ContentRepository contentRepository;

    private ContentPopularityService service;

    @BeforeEach
    void setUp() {
        service = new ContentPopularityService(contentRepository);
    }

    @Test
    @DisplayName("존재하는 콘텐츠의 시청 횟수를 1 증가시킨다")
    void increaseWatcherCount_success() {
        UUID contentId = UUID.randomUUID();

        when(contentRepository.addWatcherCount(contentId, 1L))
                .thenReturn(1);

        service.increaseWatcherCount(contentId);

        verify(contentRepository)
                .addWatcherCount(contentId, 1L);
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠이면 예외가 발생한다")
    void increaseWatcherCount_failsWhenContentDoesNotExist() {
        UUID contentId = UUID.randomUUID();

        when(contentRepository.addWatcherCount(contentId, 1L))
                .thenReturn(0);

        assertThatThrownBy(
                () -> service.increaseWatcherCount(contentId)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "존재하지 않는 콘텐츠입니다: "
                                + contentId
                );

        verify(contentRepository)
                .addWatcherCount(contentId, 1L);
    }
}