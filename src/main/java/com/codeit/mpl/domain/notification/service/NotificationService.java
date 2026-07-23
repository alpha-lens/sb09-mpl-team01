package com.codeit.mpl.domain.notification.service;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import org.springframework.transaction.annotation.Propagation;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class NotificationService {
  private final NotificationRepository notificationRepository;
  private final DirectMessageRepository directMessageRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationDto saveNotification(NotificationEvent event) {
    log.info("[NotificationService] 알림 생성 시작 - receiverId: {}, senderId: {}, level: {}, title: {}",
        event.getReceiver().getId(),
        event.getSender() != null ? event.getSender().getId() : "SYSTEM",
        event.getLevel(),
        event.getTitle()
    );

    String title = event.getTitle();
    if (event.getType() != null && event.getTargetId() != null) {
        var existingOpt = notificationRepository.findByReceiverIdAndTypeAndTargetIdAndIsReadFalse(
            event.getReceiver().getId(),
            event.getType(),
            event.getTargetId()
        );
        if (existingOpt.isPresent()) {
            notificationRepository.deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(
                event.getReceiver().getId(),
                event.getType(),
                event.getTargetId()
            );
            notificationRepository.flush();

            if (event.getType() == NotificationType.DM) {
                long unreadCount = directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(
                    event.getTargetId(),
                    event.getReceiver().getId()
                );
                title = event.getSender().getName() + "가 보낸 메시지가 " + unreadCount + "건 있습니다";
            }
        }
    }

    Notification notification = Notification.builder()
        .receiver(event.getReceiver())
        .sender(event.getSender())
        .level(event.getLevel())
        .type(event.getType())
        .targetId(event.getTargetId())
        .title(title)
        .content(event.getContent())
        .isRead(false)
        .build();

    Notification saved = notificationRepository.save(notification);
    log.info("[NotificationService] 알림 생성 완료 - notificationId: {}", saved.getId());

    return new NotificationDto(
        saved.getId(),
        saved.getCreatedAt(),
        saved.getReceiver().getId(),
        saved.getTitle(),
        saved.getContent(),
        saved.getLevel()
    );
  }

  @Transactional(readOnly = true)
  public CursorPageResponseDto<NotificationDto> getNotifications(
      UUID userId, String cursorStr, UUID idAfter, int limit, Direction sortDirection
  ) {
    log.debug("[NotificationService] 알림 조회 시작 - userId: {}, limit: {}, sortDirection: {}", userId, limit, sortDirection);
    Instant cursor = (cursorStr != null && !cursorStr.isBlank()) ? Instant.parse(cursorStr) : null;

    List<Notification> list = notificationRepository.findNotificationsWithCursor(
        userId, cursor, idAfter, limit, sortDirection
    );

    boolean hasNext = list.size() > limit;
    if (hasNext) {
      list = list.subList(0, limit);
    }

    List<NotificationDto> dtos = list.stream()
        .map(n -> new NotificationDto(n.getId(), n.getCreatedAt(), n.getReceiver().getId(), n.getTitle(), n.getContent(), n.getLevel()))
            .toList();

    long totalCount = notificationRepository.countByReceiverId(userId);

    String nextCursor = null;
    String nextIdAfter = null;
    if (hasNext && !dtos.isEmpty()) {
      NotificationDto last = dtos.get(dtos.size() - 1);
      nextCursor = last.createdAt().toString();
      nextIdAfter = last.id().toString();
    }

    log.debug("[NotificationService] 알림 조회 완료 - userId: {}, 조회 건수: {}, totalCount: {}, hasNext: {}", userId, dtos.size(), totalCount, hasNext);

    return new CursorPageResponseDto<>(dtos, nextCursor, nextIdAfter, hasNext, totalCount, "createdAt", sortDirection);
  }

  public void deleteNotification(UUID notificationId) {
    log.info("[NotificationService] 알림 삭제 시작 - notificationId: {}", notificationId);
    notificationRepository.deleteById(notificationId);
    log.info("[NotificationService] 알림 삭제 완료 - notificationId: {}", notificationId);
  }
}
