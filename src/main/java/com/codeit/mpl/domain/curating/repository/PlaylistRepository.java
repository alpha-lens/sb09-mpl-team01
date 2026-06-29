package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.user.entity.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

  boolean existsByOwnerAndId(User owner, UUID id);

  @Query("""
        SELECT p FROM Playlist p
        WHERE (:keywordLike IS NULL OR p.title LIKE %:keywordLike%)
        AND (:ownerIdEqual IS NULL OR p.owner.id = :ownerIdEqual)
        AND (:subscriberIdEqual IS NULL OR EXISTS (
            SELECT ps FROM PlaylistSubscription ps
            WHERE ps.playlist = p AND ps.subscriber.id = :subscriberIdEqual
        ))
        """)
  Page<Playlist> findAllWithFilters(
      @Param("keywordLike") String keywordLike,
      @Param("ownerIdEqual") UUID ownerIdEqual,
      @Param("subscriberIdEqual") UUID subscriberIdEqual,
      Pageable pageable
  );
}