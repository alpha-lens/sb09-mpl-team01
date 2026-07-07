package com.codeit.mpl.domain.chat.controller;

import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WatchingSessionController {

    private final WatchingSessionService watchingSessionService;

    @GetMapping("/users/{watcherId}/watching-sessions")
    public ResponseEntity<WatchingSessionDto> findWatchingSessionByWatcher(@PathVariable UUID watcherId) {
        log.info("특정 사용자의 시청 세션 조회. watcherId={}", watcherId);
        WatchingSessionDto dto = watchingSessionService.findWatchingSessionByWatcher(watcherId);
        if (dto == null) {
            return ResponseEntity.ok().build();
        }
        log.info("실시간 시청 세션 응답: WatchingSessionDto={}", dto);
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/contents/{contentId}/watching-sessions")
    public ResponseEntity<CursorPageResponseDto<WatchingSessionDto>> findWatchingSessionsByContent(
        @PathVariable UUID contentId,
        @RequestParam(value = "watcherNameLike", required = false) String watcherNameLike,
        @ModelAttribute CursorPageRequestDto request
    ) {
        log.info("특정 콘텐츠의 시청 세션 목록 조회. contentId={}", contentId);
        CursorPageResponseDto<WatchingSessionDto> response =
            watchingSessionService.findWatchingSessionsByContent(contentId, watcherNameLike, request);
        log.info("시청 세션 목록 반환. response={}", response);
        return ResponseEntity.ok(response);
    }
}
