package com.codeit.mpl.domain.notification.service;

import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private DirectMessageRepository directMessageRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User receiver;
    private User sender;
    private UUID receiverId;
    private UUID senderId;

    @BeforeEach
    void setUp() throws Exception {
        receiverId = UUID.randomUUID();
        senderId = UUID.randomUUID();
        receiver = User.builder().email("rec@test.com").name("Receiver").build();
        sender = User.builder().email("sen@test.com").name("Sender").build();

        Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(receiver, receiverId);
        idField.set(sender, senderId);
    }

    @Test
    @DisplayName("saveNotification - 일반 알림 생성 테스트")
    void saveNotification_normal() {
        NotificationEvent event = new NotificationEvent(
                receiver, sender, NotificationLevel.INFO, "Title", "Content", null, null
        );

        Notification saved = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .level(NotificationLevel.INFO)
                .title("Title")
                .content("Content")
                .isRead(false)
                .build();

        try {
            Field field = Notification.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(saved, UUID.randomUUID());
            Field fieldTime = Notification.class.getDeclaredField("createdAt");
            fieldTime.setAccessible(true);
            fieldTime.set(saved, Instant.now());
        } catch (Exception ignored) {}

        given(notificationRepository.save(any(Notification.class))).willReturn(saved);

        NotificationDto dto = notificationService.saveNotification(event);

        assertThat(dto).isNotNull();
        assertThat(dto.title()).isEqualTo("Title");
    }

    @Test
    @DisplayName("saveNotification - 기존 읽지 않은 알림 갱신 처리 (DM 타입)")
    void saveNotification_dmDuplicate() {
        UUID targetId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent(
                receiver, sender, NotificationLevel.INFO, "Title", "Content", NotificationType.DM, targetId
        );

        Notification existing = Notification.builder().receiver(receiver).sender(sender).build();
        given(notificationRepository.findByReceiverIdAndTypeAndTargetIdAndIsReadFalse(receiverId, NotificationType.DM, targetId))
                .willReturn(Optional.of(existing));
        given(directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(targetId, receiverId)).willReturn(3L);

        Notification saved = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .title("Sender가 보낸 메시지가 3건 있습니다")
                .build();

        given(notificationRepository.save(any(Notification.class))).willReturn(saved);

        NotificationDto result = notificationService.saveNotification(event);

        verify(notificationRepository).deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(receiverId, NotificationType.DM, targetId);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("getNotifications - 커서 페이징 조회")
    void getNotifications_paging() {
        given(notificationRepository.findNotificationsWithCursor(any(), any(), any(), eq(10), any()))
                .willReturn(List.of());
        given(notificationRepository.countByReceiverId(receiverId)).willReturn(0L);

        CursorPageResponseDto<NotificationDto> result = notificationService.getNotifications(
                receiverId, null, null, 10, Direction.DESCENDING
        );

        assertThat(result.data()).isEmpty();
    }

    @Test
    @DisplayName("deleteNotification - 알림 ID로 삭제")
    void deleteNotification_success() {
        UUID id = UUID.randomUUID();
        notificationService.deleteNotification(id);

        verify(notificationRepository).deleteById(id);
    }
}
