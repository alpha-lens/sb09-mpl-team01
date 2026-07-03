package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.WatchingSession;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WatchingSessionRepository extends JpaRepository<WatchingSession, UUID> {

    long countByContent(Content content);

    @Query("SELECT w.content.id, COUNT(w) FROM WatchingSession w WHERE w.content.id IN :contentIds GROUP BY w.content.id")
    List<Object[]> findWatcherCountsByContentIds(@Param("contentIds") List<UUID> contentIds);
}