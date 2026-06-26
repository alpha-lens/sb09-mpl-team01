package com.codeit.mpl.domain.notification.service;

import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class NotificationService {
  private final NotificationRepository notificationRepository;

  /**
   * Retrieves notifications for a user using cursor-based pagination.
   *
   * @param userId  the user whose notifications to retrieve
   * @param cursor  the pagination cursor from a previous response; null for the first page
   * @param idAfter notifications created after this timestamp
   * @param limit   the maximum number of notifications to return per page
   * @return        a paginated response containing notifications and pagination metadata
   */
  @Transactional(readOnly = true)
  public CursorPageResponseDto<NotificationDto> getNotifications(
      UUID userId, String cursor, Instant idAfter, int limit
  ) {
    Pageable pageable = PageRequest.of(0, limit + 1);
    List<Notification> list = notificationRepository.findNotifications(userId, idAfter, pageable);

    boolean hasNext = list.size() > limit;
    if (hasNext) {
        list = list.subList(0, limit);
    }

    List<NotificationDto> dtos = list.stream()
        .map(n -> new NotificationDto(
            n.getId(),
            n.getCreatedAt(),
            n.getReceiver().getId(),
            n.getTitle(),
            n.getContent(),
            n.getLevel()
        ))
        .toList();

    String nextCursor = null;
    String nextIdAfter = null;
    if (!dtos.isEmpty()) {
        NotificationDto last = dtos.get(dtos.size() - 1);
        nextCursor = last.id().toString();
        nextIdAfter = last.createdAt().toString();
    }

    long totalCount = notificationRepository.countByReceiverId(userId);

    return new CursorPageResponseDto<>(
        dtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        "createdAt",
        Direction.DESCENDING
    );
  }

  /**
   * Deletes the notification with the specified ID.
   *
   * @param notificationId the ID of the notification to delete
   */
  public void deleteNotification(UUID notificationId) {
    notificationRepository.deleteById(notificationId);
  }
}
