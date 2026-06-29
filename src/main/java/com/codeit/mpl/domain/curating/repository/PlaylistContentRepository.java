package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistContent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  boolean existsByPlaylistAndContent(Playlist playlist, Content content);

  void deleteByPlaylistAndContent(Playlist playlist, Content content);
}