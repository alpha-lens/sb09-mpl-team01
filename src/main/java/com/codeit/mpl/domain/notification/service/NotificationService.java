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

import com.codeit.mpl.domain.notification.event.NotificationEvent;
import org.springframework.transaction.annotation.Propagation;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class NotificationService {
  private final NotificationRepository notificationRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public NotificationDto saveNotification(NotificationEvent event) {
    Notification notification = Notification.builder()
        .receiver(event.getReceiver())
        .sender(event.getSender())
        .level(event.getLevel())
        .title(event.getTitle())
        .content(event.getContent())
        .isRead(false)
        .build();

    Notification saved = notificationRepository.save(notification);

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

    return new CursorPageResponseDto<>(dtos, nextCursor, nextIdAfter, hasNext, totalCount, "createdAt", sortDirection);
  }

  public void deleteNotification(UUID notificationId) {
    notificationRepository.deleteById(notificationId);
  }
}
