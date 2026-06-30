package com.codeit.mpl.domain.chat.controller;

import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WatchingSessionController {

    private final WatchingSessionService watchingSessionService;

    @GetMapping("/users/{watcherId}/watching-sessions")
    public ResponseEntity<WatchingSessionDto> findWatchingSessionByWatcher(@PathVariable UUID watcherId) {
        WatchingSessionDto dto = watchingSessionService.findWatchingSessionByWatcher(watcherId);
        if (dto == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/contents/{contentId}/watching-sessions")
    public ResponseEntity<CursorPageResponseDto<WatchingSessionDto>> findWatchingSessionsByContent(
        @PathVariable UUID contentId,
        @RequestParam(value = "watcherNameLike", required = false) String watcherNameLike,
        @ModelAttribute CursorPageRequestDto request
    ) {
        CursorPageResponseDto<WatchingSessionDto> response =
            watchingSessionService.findWatchingSessionsByContent(contentId, watcherNameLike, request);
        return ResponseEntity.ok(response);
    }
}
