package com.codeit.mpl.domain.notification.dto;

import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {

}
