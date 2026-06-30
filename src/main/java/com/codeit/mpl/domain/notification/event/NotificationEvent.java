package com.codeit.mpl.domain.notification.event;

import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class NotificationEvent {
    private final User receiver;
    private final User sender;
    private final NotificationLevel level;
    private final String title;
    private final String content;
}
