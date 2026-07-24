package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.WatchingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WatchingSessionRepository extends JpaRepository<WatchingSession, UUID> {

    long countByContent(Content content);

    @Query("SELECT ws.content.id, COUNT(ws) FROM WatchingSession ws WHERE ws.content.id IN :contentIds GROUP BY ws.content.id")
    List<Object[]> findWatcherCountsByContentIds(@Param("contentIds") List<UUID> contentIds);
}