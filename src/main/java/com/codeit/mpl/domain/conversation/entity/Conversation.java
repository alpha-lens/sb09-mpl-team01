package com.codeit.mpl.domain.conversation.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "conversation", uniqueConstraints = {
    @UniqueConstraint(
        name = "uk_user1_user2",
        columnNames = {"user1_id", "user2_id"}
    )
})
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Conversation extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user1_id", nullable = false)
  private User user1;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user2_id", nullable = false)
  private User user2;

  public static Conversation create(User user1, User user2) {
    boolean isUser1Smaller = user1.getId().toString().compareTo(user2.getId().toString()) < 0;
    User orderedUser1 = isUser1Smaller ? user1 : user2;
    User orderedUser2 = isUser1Smaller ? user2 : user1;

    return Conversation.builder()
        .user1(orderedUser1)
        .user2(orderedUser2)
        .build();
  }
}
