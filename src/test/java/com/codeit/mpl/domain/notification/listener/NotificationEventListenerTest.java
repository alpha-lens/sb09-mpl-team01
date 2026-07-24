package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationKafkaMessage;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    @DisplayName("handleNotificationEvent - Event 수신 시 Kafka 메세지 전송")
    void handleNotificationEvent_success() {
        User receiver = User.builder().build();
        User sender = User.builder().build();

        NotificationEvent event = new NotificationEvent(
                receiver,
                sender,
                NotificationLevel.INFO,
                "Test Title",
                "Test Content",
                NotificationType.DM,
                UUID.randomUUID()
        );

        notificationEventListener.handleNotificationEvent(event);

        verify(kafkaTemplate).send(eq("notification-topic"), any(NotificationKafkaMessage.class));
    }
}
