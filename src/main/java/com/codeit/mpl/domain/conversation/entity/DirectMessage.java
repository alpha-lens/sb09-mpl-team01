package com.codeit.mpl.domain.conversation.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DirectMessage extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conversation_id", nullable = false)
  private Conversation conversation;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "sender_id", nullable = false)
  private User sender;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "receiver_id", nullable = false)
  private User receiver;

  @Column(name = "content")
  private String content;

  @Column(name = "is_read")
  private boolean isRead;

  public void read() {
    this.isRead = true;
  }
}
