package com.codeit.mpl.domain.notification.repository;

import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationCustomRepository {
  List<Notification> findNotificationsWithCursor(
      UUID receiverId,
      Instant cursor,
      UUID idAfter,
      int limit,
      Direction sortDirection
  );
}