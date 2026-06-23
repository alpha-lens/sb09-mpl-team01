package com.codeit.mpl.kafka;

import com.codeit.mpl.websocket.WebHooks.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Kafka {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes a message to the specified Kafka topic.
     *
     * @param topic the Kafka topic name
     * @param message the message object to publish
     */
    public void send(String topic, Object message) {
        kafkaTemplate.send(topic, message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("[Kafka] 메시지 발행 성공. topic={}, offset={}", 
                                topic, result.getRecordMetadata().offset());
                    } else {
                        log.error("[Kafka] 메시지 발행 실패. topic={}, error={}", 
                                topic, ex.getMessage());
                    }
                });
    }

    /**
     * Consumes chat messages from the chat topic.
     *
     * @param message the chat message received from Kafka
     */
    @KafkaListener(topics = "chat-topic", groupId = "mpl-group")
    public void consumeChatMessage(ChatMessage message) {
        log.info("[Kafka] 채팅 메시지 수신 (영속화용): room={}, sender={}, message={}",
                message.getRoomId(), message.getSender(), message.getMessage());
        
        // TODO: 여기서 DB(RDB/NoSQL/Elasticsearch)에 대량 쓰기 비동기 영속화 작업 수행
    }

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
