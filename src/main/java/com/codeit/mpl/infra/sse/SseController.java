package com.codeit.mpl.infra.sse;

import com.codeit.mpl.infra.security.UserPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SseController {

    private final SseService sseService;

    @GetMapping(value = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestHeader(value = "Last-Event-ID", required = false, defaultValue = "") String lastEventId
    ) {
        if (userPrincipal == null) {
            throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
        }
        UUID userId = userPrincipal.userId();
        if (userId == null) {
            throw new IllegalArgumentException("존재하지 않는 유저입니다.");
        }

        log.info("[SSE] /api/sse 구독 요청. userId={}, Last-Event-ID={}", userId, lastEventId);
        SseEmitter emitter = sseService.subscribe(userId, lastEventId);
        return ResponseEntity.ok(emitter);
    }
}
