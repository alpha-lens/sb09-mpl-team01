package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistSubscription;
import com.codeit.mpl.domain.user.entity.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {

  boolean existsByPlaylistAndSubscriber(Playlist playlist, User subscriber);

  void deleteByPlaylistAndSubscriber(Playlist playlist, User subscriber);

  long countByPlaylist(Playlist playlist);

  void deleteByPlaylist(Playlist playlist);

  @Query("SELECT p.id, COUNT(ps.id), " +
      "SUM(CASE WHEN ps.subscriber.id = :subscriberId THEN 1 ELSE 0 END) " +
      "FROM Playlist p " +
      "LEFT JOIN PlaylistSubscription ps ON ps.playlist = p " +
      "WHERE p.id IN :playlistIds " +
      "GROUP BY p.id")
  List<Object[]> findSubscriptionStats(@Param("playlistIds") List<UUID> playlistIds, @Param("subscriberId") UUID subscriberId);
}