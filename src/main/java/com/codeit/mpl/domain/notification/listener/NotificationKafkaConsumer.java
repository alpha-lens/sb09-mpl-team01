package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationKafkaMessage;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("!test")
@Component
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final UserRepository userRepository;
    private final NotificationAsyncHandler notificationAsyncHandler;

    /**
     * Consumes notification events from the Kafka notification topic.
     *
     * <p>Exceptions are intentionally not caught here so that they propagate to the
     * {@code DefaultErrorHandler} configured in {@code KafkaConfig}. The error handler will:
     * <ul>
     *   <li>Retry up to 3 times with exponential backoff for transient failures.</li>
     *   <li>Publish the failed record to {@code notification-topic.DLT} after all retries
     *       are exhausted.</li>
     *   <li>Send {@link IllegalArgumentException} (e.g. receiver not found) directly to the
     *       DLT without retrying, as these are non-recoverable business errors.</li>
     * </ul>
     *
     * @param message the notification event payload to process
     */
    @KafkaListener(topics = "notification-topic", groupId = "mpl-group")
    public void consumeNotificationEvent(NotificationKafkaMessage message) {
        log.info("[Kafka] 알림 이벤트 수신: {}", message);

        User receiver = userRepository.findById(message.receiverId())
                .orElseThrow(() -> new IllegalArgumentException("Receiver not found: " + message.receiverId()));

        User sender = null;
        if (message.senderId() != null) {
            sender = userRepository.findById(message.senderId()).orElse(null);
        }

        NotificationEvent event = new NotificationEvent(
                receiver,
                sender,
                message.level(),
                message.title(),
                message.content(),
                message.type(),
                message.targetId()
        );

        notificationAsyncHandler.process(event);
        log.info("[Kafka] 알림 이벤트 처리 완료: receiverId={}", message.receiverId());
    }
}
