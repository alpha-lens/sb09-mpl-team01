package com.codeit.mpl.domain.curating.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "playlist_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_playlist_subscriptions_playlist")
  )
  private Playlist playlist;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "subscriber_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_playlist_subscriptions_subscriber")
  )
  private User subscriber;

  public PlaylistSubscription(Playlist playlist, User subscriber) {
    this.playlist = playlist;
    this.subscriber = subscriber;
  }
}