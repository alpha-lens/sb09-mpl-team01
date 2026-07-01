package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistSubscription;
import com.codeit.mpl.domain.user.entity.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {

  boolean existsByPlaylistAndSubscriber(Playlist playlist, User subscriber);

  void deleteByPlaylistAndSubscriber(Playlist playlist, User subscriber);

  long countByPlaylist(Playlist playlist);

  void deleteByPlaylist(Playlist playlist);
}