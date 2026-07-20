package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistContent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

  boolean existsByPlaylistAndContent(Playlist playlist, Content content);

  void deleteByPlaylistAndContent(Playlist playlist, Content content);

  @Query("SELECT pc FROM PlaylistContent pc JOIN FETCH pc.content c LEFT JOIN FETCH c.tags WHERE pc.playlist = :playlist")
  List<PlaylistContent> findByPlaylist(@Param("playlist") Playlist playlist);

  void deleteByPlaylist(Playlist playlist);

  // N+1 문제 해결을 위한 IN 절 일괄 조회 쿼리 추가
  @Query("SELECT pc FROM PlaylistContent pc JOIN FETCH pc.content c LEFT JOIN FETCH c.tags WHERE pc.playlist.id IN :playlistIds")
  List<PlaylistContent> findByPlaylistIdIn(@Param("playlistIds") List<UUID> playlistIds);
}