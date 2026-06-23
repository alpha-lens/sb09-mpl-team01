package com.codeit.mpl.service;

import com.codeit.mpl.dto.response.CursorPageResponseDto;
import com.codeit.mpl.dto.response.Direction;
import com.codeit.mpl.dto.response.NotificationDto;
import com.codeit.mpl.repository.NotificationRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    // 뼈대 구현: 일단 빈 리스트를 반환하여 컴파일 에러를 해결하고 비즈니스 흐름을 만듭니다.
    return new CursorPageResponseDto<>(
        Collections.emptyList(),
        null,
        null,
        false,
        0L,
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
