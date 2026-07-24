package com.codeit.mpl.domain.notification.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Table(
    name = "notifications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_notifications_receiver_type_target",
            columnNames = {"receiver_id", "type", "target_id"}
        )
    }
)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Notification extends BaseEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @Enumerated(EnumType.STRING)
  @Column(name = "level")
  private NotificationLevel level;

  @Enumerated(EnumType.STRING)
  @Column(name = "type")
  private NotificationType type;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(name = "title")
  private String title;

  @Column(name = "content")
  private String content;

  @Column(name = "is_read")
  private boolean isRead;
}
