package com.codeit.mpl.domain.curating.entity;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.infra.common.entity.base.BaseEntity;
import jakarta.persistence.*;
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "playlist_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_playlist_contents_playlist")
  )
  private Playlist playlist;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(
      name = "content_id",
      nullable = false,
      foreignKey = @ForeignKey(name = "fk_playlist_contents_content")
  )
  private Content content;

  public PlaylistContent(Playlist playlist, Content content) {
    this.playlist = playlist;
    this.content = content;
  }
}