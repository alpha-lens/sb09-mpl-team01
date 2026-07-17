package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationKafkaMessage;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("[NotificationEventListener] Spring Event 수신 -> Kafka 발행 시작: receiverId={}", event.getReceiver().getId());
        
        NotificationKafkaMessage message = new NotificationKafkaMessage(
                event.getReceiver().getId(),
                event.getSender() != null ? event.getSender().getId() : null,
                event.getLevel(),
                event.getTitle(),
                event.getContent(),
                event.getType(),
                event.getTargetId()
        );

        kafkaTemplate.send("notification-topic", message);
    }
}
