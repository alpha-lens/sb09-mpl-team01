package com.codeit.mpl.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SseService {

    private static final Long DEFAULT_TIMEOUT = 60L * 1000 * 60; // 1시간
    private final SseEmitterRepository sseEmitterRepository;

    public SseEmitter subscribe(UUID userId, String lastEventId) {
        String emitterId = makeTimeIncludeId(userId);
        SseEmitter emitter = sseEmitterRepository.save(emitterId, new SseEmitter(DEFAULT_TIMEOUT));

        emitter.onCompletion(() -> sseEmitterRepository.deleteById(emitterId));
        emitter.onTimeout(() -> sseEmitterRepository.deleteById(emitterId));
        emitter.onError((e) -> sseEmitterRepository.deleteById(emitterId));

        // 503 에러 방지용 더미 이벤트 전송
        String eventId = makeTimeIncludeId(userId);
        sendNotification(emitter, eventId, emitterId, "EventStream Created. [userId=" + userId + "]");

        // 클라이언트가 미수신한 이벤트가 존재할 경우 전송하여 유실 예방
        if (hasLostData(lastEventId)) {
            sendLostData(lastEventId, userId.toString(), emitterId, emitter);
        }

        return emitter;
    }

    public void send(UUID receiverId, Object data, String eventName) {
        String eventId = makeTimeIncludeId(receiverId);
        Map<String, SseEmitter> emitters = sseEmitterRepository.findAllEmitterStartWithByMemberId(receiverId.toString());
        
        emitters.forEach(
                (key, emitter) -> {
                    // 이벤트 유실을 대비하여 캐시에 저장
                    sseEmitterRepository.saveEventCache(key, data);
                    // 데이터 전송
                    sendNotification(emitter, eventId, key, data);
                }
        );
    }

    private String makeTimeIncludeId(UUID userId) {
        return userId.toString() + "_" + System.currentTimeMillis();
    }

    private void sendNotification(SseEmitter emitter, String eventId, String emitterId, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .id(eventId)
                    .data(data));
        } catch (IOException exception) {
            sseEmitterRepository.deleteById(emitterId);
            log.error("SSE 연결 전송 중 오류 발생: {}", exception.getMessage());
        }
    }

    private boolean hasLostData(String lastEventId) {
        return lastEventId != null && !lastEventId.isEmpty();
    }

    private void sendLostData(String lastEventId, String memberId, String emitterId, SseEmitter emitter) {
        Map<String, Object> eventCaches = sseEmitterRepository.findAllEventCacheStartWithByMemberId(memberId);
        eventCaches.entrySet().stream()
                .filter(entry -> lastEventId.compareTo(entry.getKey()) < 0)
                .forEach(entry -> sendNotification(emitter, entry.getKey(), emitterId, entry.getValue()));
    }
}
