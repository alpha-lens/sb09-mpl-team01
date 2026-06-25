package com.codeit.mpl.domain.curating.entity;

import com.codeit.mpl.infra.common.entity.base.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "playlists")
public class Playlist extends BaseUpdatableEntity {

  @Column(name = "owner_id", nullable = false)
  private UUID ownerId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "description")
  private String description;

  public Playlist(UUID ownerId, String title, String description) {
    this.ownerId = ownerId;
    this.title = title;
    this.description = description;
  }

  public void update(String title, String description) {
    if (title != null) this.title = title;
    if (description != null) this.description = description;
  }
}