package com.codeit.mpl.domain.profile.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
    name = "follows",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_follower_followee",
            columnNames = {"follower_id", "followee_id"}
        )
    }
)
public class Follow extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "follower_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_follows_follower")
  )
  private User follower;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "followee_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_follows_followee")
  )
  private User followee;

  public Follow(User follower, User followee) {
    this.follower = follower;
    this.followee = followee;
  }
}