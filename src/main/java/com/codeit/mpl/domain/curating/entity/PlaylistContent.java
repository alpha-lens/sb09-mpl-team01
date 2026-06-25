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
    name = "playlist_contents",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"playlist_id", "content_id"})
    }
)
public class PlaylistContent extends BaseEntity {

  @Column(name = "playlist_id", nullable = false)
  private UUID playlistId;

  @Column(name = "content_id", nullable = false)
  private UUID contentId;

  public PlaylistContent(UUID playlistId, UUID contentId) {
    this.playlistId = playlistId;
    this.contentId = contentId;
  }
}