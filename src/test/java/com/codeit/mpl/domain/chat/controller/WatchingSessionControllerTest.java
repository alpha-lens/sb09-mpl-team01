package com.codeit.mpl.domain.chat.controller;

import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class WatchingSessionControllerTest {

    @Mock
    private WatchingSessionService watchingSessionService;

    @InjectMocks
    private WatchingSessionController watchingSessionController;

    @Test
    @DisplayName("특정 사용자의 시청 세션이 존재하는 경우 DTO를 200 OK로 반환한다")
    void findWatchingSessionByWatcher_whenExists_returnsDto() {
        // given
        UUID watcherId = UUID.randomUUID();
        WatchingSessionDto expectedDto = mock(WatchingSessionDto.class);
        given(watchingSessionService.findWatchingSessionByWatcher(watcherId)).willReturn(expectedDto);

        // when
        ResponseEntity<WatchingSessionDto> response = watchingSessionController.findWatchingSessionByWatcher(watcherId);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(expectedDto);
    }

    @Test
    @DisplayName("특정 사용자의 시청 세션이 없는 경우 200 OK 빈 응답을 반환한다")
    void findWatchingSessionByWatcher_whenNotExists_returnsEmptyResponse() {
        // given
        UUID watcherId = UUID.randomUUID();
        given(watchingSessionService.findWatchingSessionByWatcher(watcherId)).willReturn(null);

        // when
        ResponseEntity<WatchingSessionDto> response = watchingSessionController.findWatchingSessionByWatcher(watcherId);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("특정 콘텐츠의 시청 세션 목록 조회 시 커서 페이징 결과를 200 OK로 반환한다")
    void findWatchingSessionsByContent_returnsCursorPageResponse() {
        // given
        UUID contentId = UUID.randomUUID();
        String watcherNameLike = "우디";
        CursorPageRequestDto request = new CursorPageRequestDto(null, null, 10, null, null);

        CursorPageResponseDto<WatchingSessionDto> pageResponse = new CursorPageResponseDto<>(
                List.of(), null, null, false, 0, "createdAt", Direction.DESCENDING
        );

        given(watchingSessionService.findWatchingSessionsByContent(eq(contentId), eq(watcherNameLike), any(CursorPageRequestDto.class)))
                .willReturn(pageResponse);

        // when
        ResponseEntity<CursorPageResponseDto<WatchingSessionDto>> response =
                watchingSessionController.findWatchingSessionsByContent(contentId, watcherNameLike, request);

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(pageResponse);
    }
}
