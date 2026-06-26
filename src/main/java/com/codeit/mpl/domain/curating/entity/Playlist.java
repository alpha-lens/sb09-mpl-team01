package com.codeit.mpl.domain.curating.entity;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "playlists")
public class Playlist extends BaseUpdatableEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "owner_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_playlists_owner")
  )
  private User owner;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description")
  private String description;

  public Playlist(User owner, String title, String description) {
    this.owner = owner;
    this.title = title;
    this.description = description;
  }

  public void update(String title, String description) {
    if (title != null) this.title = title;
    if (description != null) this.description = description;
  }
}