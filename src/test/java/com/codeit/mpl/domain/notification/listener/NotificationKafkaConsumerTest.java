package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationKafkaMessage;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaConsumerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationAsyncHandler notificationAsyncHandler;

    @InjectMocks
    private NotificationKafkaConsumer notificationKafkaConsumer;

    private User createUser(UUID id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("Kafka 메시지를 수신하여 NotificationAsyncHandler로 이벤트를 전달한다")
    void consumeNotificationEvent_Success() {
        // given
        UUID receiverId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        User receiver = createUser(receiverId);
        User sender = createUser(senderId);

        NotificationKafkaMessage message = new NotificationKafkaMessage(
                receiverId, senderId, null, "Title", "Content", null, null
        );

        given(userRepository.findById(receiverId)).willReturn(Optional.of(receiver));
        given(userRepository.findById(senderId)).willReturn(Optional.of(sender));

        // when
        notificationKafkaConsumer.consumeNotificationEvent(message);

        // then
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationAsyncHandler).process(captor.capture());
        assertThat(captor.getValue().getReceiver()).isEqualTo(receiver);
        assertThat(captor.getValue().getSender()).isEqualTo(sender);
    }

    @Test
    @DisplayName("수신자(receiver)가 없으면 IllegalArgumentException이 발생한다")
    void consumeNotificationEvent_ReceiverNotFound() {
        // given
        UUID receiverId = UUID.randomUUID();
        NotificationKafkaMessage message = new NotificationKafkaMessage(
                receiverId, null, null, "Title", "Content", null, null
        );

        given(userRepository.findById(receiverId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationKafkaConsumer.consumeNotificationEvent(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Receiver not found");
    }
}
