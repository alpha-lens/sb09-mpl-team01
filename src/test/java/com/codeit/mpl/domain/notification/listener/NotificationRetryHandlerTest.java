package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class NotificationRetryHandlerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationRetryHandler notificationRetryHandler;

    @Test
    @DisplayName("saveWithRetry 호출 시 NotificationService.saveNotification을 호출한다")
    void saveWithRetry() {
        // given
        NotificationEvent event = new NotificationEvent(null, null, NotificationLevel.INFO, "title", "content", null, null);
        NotificationDto expected = new NotificationDto(UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "title", "content", NotificationLevel.INFO);

        given(notificationService.saveNotification(event)).willReturn(expected);

        // when
        NotificationDto result = notificationRetryHandler.saveWithRetry(event);

        // then
        assertThat(result).isEqualTo(expected);
    }
}
