package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.listener.NotificationRedisListener.RedisNotificationWrapper;
import com.codeit.mpl.infra.sse.SseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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
            // 2. Redis Pub/Sub 발행 (ObjectMapper를 사용한 안전한 JSON 직렬화)
            String message = objectMapper.writeValueAsString(
                new RedisNotificationWrapper(dto.receiverId(), dto)
            );
            redisTemplate.convertAndSend("ch-notification", message);
            log.info("[Redis Pub] 알림 이벤트 발행 성공. receiverId={}, title={}", event.getReceiver().getId(), event.getTitle());
        } catch (JsonProcessingException e) {
            log.error("Redis 메시지 직렬화 실패. receiverId={}", event.getReceiver().getId(), e);
            sseService.sendLocal(event.getReceiver().getId(), dto, "notifications");
        } catch (Exception e) {
            log.error("Redis 발행 실패 - 로컬 폴백 수행. receiverId={}", event.getReceiver().getId(), e);
            // Redis 장애 시 로컬 SSE 전송으로 폴백
            sseService.sendLocal(event.getReceiver().getId(), dto, "notifications");
        }
    }
}
