package com.codeit.mpl.domain.notification.event;

import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class NotificationEvent {
    private final User receiver;
    private final User sender;
    private final NotificationLevel level;
    private final String title;
    private final String content;
    private final NotificationType type;
    private final UUID targetId;

    public NotificationEvent(User receiver, User sender, NotificationLevel level, String title, String content) {
        this.receiver = receiver;
        this.sender = sender;
        this.level = level;
        this.title = title;
        this.content = content;
        this.type = null;
        this.targetId = null;
    }
}
