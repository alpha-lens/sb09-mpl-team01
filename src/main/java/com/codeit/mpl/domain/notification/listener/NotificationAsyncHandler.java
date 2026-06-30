package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.infra.sse.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationAsyncHandler {

    private final NotificationRetryHandler notificationRetryHandler;
    private final StringRedisTemplate redisTemplate;
    private final SseService sseService;

    @Async("notificationExecutor")
    public void process(NotificationEvent event) {
        NotificationDto dto;
        try {
            // 1. 재시도 핸들러를 호출하여 DB 저장 (재시도 및 REQUIRES_NEW 보장)
            dto = notificationRetryHandler.saveWithRetry(event);
        } catch (Exception e) {
            log.error("Notification DB 저장 최종 실패 - 알림 발송 중단. receiver={}", event.getReceiver().getId(), e);
            return;
        }

        try {
            // 2. Redis Pub/Sub 발행 (수동 JSON 문자열 생성)
            String message = String.format(
                "{\"id\":\"%s\",\"createdAt\":\"%s\",\"receiverId\":\"%s\",\"title\":\"%s\",\"content\":\"%s\",\"level\":\"%s\"}",
                dto.id(),
                dto.createdAt() != null ? dto.createdAt().toString() : "",
                dto.receiverId(),
                dto.title().replace("\"", "\\\""),
                dto.content().replace("\"", "\\\""),
                dto.level().name()
            );

            redisTemplate.convertAndSend("notification-topic", message);
            log.info("[Redis Pub] 알림 이벤트 발행 성공. receiverId={}, title={}", event.getReceiver().getId(), event.getTitle());
        } catch (Exception e) {
            log.error("Redis 발행 실패 - 로컬 폴백 수행. receiverId={}", event.getReceiver().getId(), e);
            // Redis 장애 시 로컬 SSE 전송으로 폴백
            sseService.sendLocal(event.getReceiver().getId(), dto, "notifications");
        }
    }
}
