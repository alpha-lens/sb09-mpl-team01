package com.codeit.mpl.domain.curating.entity;

import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(
    name = "playlist_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"playlist_id", "subscriber_id"})
    }
)
public class PlaylistSubscription extends BaseEntity {

  @Column(name = "playlist_id", nullable = false)
  private UUID playlistId;

  @Column(name = "subscriber_id", nullable = false)
  private UUID subscriberId;

  public PlaylistSubscription(UUID playlistId, UUID subscriberId) {
    this.playlistId = playlistId;
    this.subscriberId = subscriberId;
  }
}