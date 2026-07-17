package com.codeit.mpl.domain.notification.dto;

import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import java.util.UUID;

public record NotificationKafkaMessage(
    UUID receiverId,
    UUID senderId,
    NotificationLevel level,
    String title,
    String content,
    NotificationType type,
    UUID targetId
) {
}
