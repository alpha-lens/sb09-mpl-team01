package com.codeit.mpl.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.notification.entity.NotificationType;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private DirectMessageRepository directMessageRepository;

  @InjectMocks
  private NotificationService notificationService;

  @Test
  @DisplayName("알림 저장 성공")
  void saveNotification_success() {
    // given
    User receiver = mock(User.class);
    User sender = mock(User.class);
    UUID receiverId = UUID.randomUUID();
    given(receiver.getId()).willReturn(receiverId);

    NotificationEvent event = new NotificationEvent(receiver, sender, NotificationLevel.INFO, "제목", "내용");
    
    Notification savedNotification = mock(Notification.class);
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    given(savedNotification.getId()).willReturn(notificationId);
    given(savedNotification.getCreatedAt()).willReturn(now);
    given(savedNotification.getReceiver()).willReturn(receiver);
    given(savedNotification.getTitle()).willReturn("제목");
    given(savedNotification.getContent()).willReturn("내용");
    given(savedNotification.getLevel()).willReturn(NotificationLevel.INFO);

    given(notificationRepository.save(any(Notification.class))).willReturn(savedNotification);

    // when
    NotificationDto result = notificationService.saveNotification(event);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(notificationId);
    assertThat(result.title()).isEqualTo("제목");
    assertThat(result.content()).isEqualTo("내용");
    assertThat(result.receiverId()).isEqualTo(receiverId);
    then(notificationRepository).should().save(any(Notification.class));
  }

  @Test
  @DisplayName("알림 목록 조회 - 다음 페이지가 존재하는 경우")
  void getNotifications_hasNext() {
    // given
    UUID userId = UUID.randomUUID();
    String cursorStr = Instant.now().toString();
    UUID idAfter = UUID.randomUUID();
    int limit = 2;
    Direction sortDirection = Direction.DESCENDING;

    User receiver = mock(User.class);
    given(receiver.getId()).willReturn(userId);

    Notification n1 = mock(Notification.class);
    given(n1.getId()).willReturn(UUID.randomUUID());
    given(n1.getCreatedAt()).willReturn(Instant.now().minusSeconds(10));
    given(n1.getReceiver()).willReturn(receiver);
    given(n1.getTitle()).willReturn("제목1");
    given(n1.getContent()).willReturn("내용1");
    given(n1.getLevel()).willReturn(NotificationLevel.INFO);

    Notification n2 = mock(Notification.class);
    given(n2.getId()).willReturn(UUID.randomUUID());
    given(n2.getCreatedAt()).willReturn(Instant.now().minusSeconds(20));
    given(n2.getReceiver()).willReturn(receiver);
    given(n2.getTitle()).willReturn("제목2");
    given(n2.getContent()).willReturn("내용2");
    given(n2.getLevel()).willReturn(NotificationLevel.INFO);

    Notification n3 = mock(Notification.class);

    List<Notification> list = List.of(n1, n2, n3);

    given(notificationRepository.findNotificationsWithCursor(any(), any(), any(), any(Integer.class), any()))
        .willReturn(list);
    given(notificationRepository.countByReceiverId(userId)).willReturn(10L);

    // when
    CursorPageResponseDto<NotificationDto> response = notificationService.getNotifications(
        userId, cursorStr, idAfter, limit, sortDirection
    );

    // then
    assertThat(response.data()).hasSize(limit);
    assertThat(response.hasNext()).isTrue();
    assertThat(response.totalCount()).isEqualTo(10L);
    assertThat(response.nextCursor()).isEqualTo(n2.getCreatedAt().toString());
    assertThat(response.nextIdAfter()).isEqualTo(n2.getId().toString());
  }

  @Test
  @DisplayName("알림 목록 조회 - 다음 페이지가 존재하지 않고 목록이 비어있는 경우")
  void getNotifications_empty() {
    // given
    UUID userId = UUID.randomUUID();
    int limit = 5;
    Direction sortDirection = Direction.DESCENDING;

    given(notificationRepository.findNotificationsWithCursor(any(), any(), any(), any(Integer.class), any()))
        .willReturn(Collections.emptyList());
    given(notificationRepository.countByReceiverId(userId)).willReturn(0L);

    // when
    CursorPageResponseDto<NotificationDto> response = notificationService.getNotifications(
        userId, null, null, limit, sortDirection
    );

    // then
    assertThat(response.data()).isEmpty();
    assertThat(response.hasNext()).isFalse();
    assertThat(response.totalCount()).isZero();
    assertThat(response.nextCursor()).isNull();
    assertThat(response.nextIdAfter()).isNull();
  }

  @Test
  @DisplayName("알림 삭제 성공")
  void deleteNotification_success() {
    // given
    UUID notificationId = UUID.randomUUID();

    // when
    notificationService.deleteNotification(notificationId);

    // then
    then(notificationRepository).should().deleteById(notificationId);
  }

  @Test
  @DisplayName("DM 알림 저장 시 기존 안 읽은 DM 알림이 있다면 삭제 후 누적 건수로 새 알림 저장")
  void saveNotification_dm_compress() {
    // given
    User receiver = mock(User.class);
    User sender = mock(User.class);
    UUID receiverId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    given(receiver.getId()).willReturn(receiverId);
    given(sender.getName()).willReturn("보낸이");

    NotificationEvent event = new NotificationEvent(receiver, sender, NotificationLevel.INFO, "새 메시지", "내용", NotificationType.DM, targetId);

    Notification existingNotification = mock(Notification.class);
    given(notificationRepository.findByReceiverIdAndTypeAndTargetIdAndIsReadFalse(receiverId, NotificationType.DM, targetId))
        .willReturn(java.util.Optional.of(existingNotification));

    given(directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(targetId, receiverId))
        .willReturn(3L);

    Notification savedNotification = mock(Notification.class);
    UUID notificationId = UUID.randomUUID();
    Instant now = Instant.now();
    given(savedNotification.getId()).willReturn(notificationId);
    given(savedNotification.getCreatedAt()).willReturn(now);
    given(savedNotification.getReceiver()).willReturn(receiver);
    given(savedNotification.getTitle()).willReturn("보낸이가 보낸 메시지가 3건 있습니다");
    given(savedNotification.getContent()).willReturn("내용");
    given(savedNotification.getLevel()).willReturn(NotificationLevel.INFO);

    given(notificationRepository.save(any(Notification.class))).willReturn(savedNotification);

    // when
    NotificationDto result = notificationService.saveNotification(event);

    // then
    assertThat(result).isNotNull();
    assertThat(result.title()).isEqualTo("보낸이가 보낸 메시지가 3건 있습니다");
    then(notificationRepository).should().deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(receiverId, NotificationType.DM, targetId);
    then(notificationRepository).should().save(any(Notification.class));
  }
}
