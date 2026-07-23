package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationRetryHandler {

    private final NotificationService notificationService;

    @Retryable(
        retryFor = {org.springframework.dao.DataAccessException.class},
        noRetryFor = {IllegalArgumentException.class, jakarta.persistence.EntityNotFoundException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public NotificationDto saveWithRetry(NotificationEvent event) {
        return notificationService.saveNotification(event);
    }
}
