package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistSubscription;
import com.codeit.mpl.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {

  boolean existsByPlaylistAndSubscriber(Playlist playlist, User subscriber);

  void deleteByPlaylistAndSubscriber(Playlist playlist, User subscriber);

  long countByPlaylist(Playlist playlist);

  void deleteByPlaylist(Playlist playlist);

  List<PlaylistSubscription> findByPlaylist(Playlist playlist);

  @Query("SELECT ps.playlist.id, COUNT(ps) FROM PlaylistSubscription ps WHERE ps.playlist.id IN :playlistIds GROUP BY ps.playlist.id")
  List<Object[]> findSubscriptionStats(@Param("playlistIds") List<UUID> playlistIds);

  @Query("SELECT ps.playlist.id FROM PlaylistSubscription ps WHERE ps.playlist.id IN :playlistIds AND ps.subscriber = :subscriber")
  List<UUID> findSubscribedPlaylistIds(@Param("playlistIds") List<UUID> playlistIds, @Param("subscriber") User subscriber);
}