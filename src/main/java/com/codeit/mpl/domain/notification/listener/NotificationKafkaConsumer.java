package com.codeit.mpl.domain.notification.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class NotificationKafkaConsumer {

    /**
     * Consumes notification events from the Kafka notification topic.
     *
     * @param notificationEvent the notification event payload to process
     */
    @KafkaListener(topics = "notification-topic", groupId = "mpl-group")
    public void consumeNotificationEvent(Object notificationEvent) {
        log.info("[Kafka] 알림 이벤트 수신: {}", notificationEvent);
        
        // TODO: 알림 히스토리 기록 및 복잡한 비즈니스 알림 룰 가공 처리
    }
}
