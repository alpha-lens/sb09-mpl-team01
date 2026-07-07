package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.WatchingSession;
import io.lettuce.core.dynamic.annotation.Param;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchingSessionRepository extends JpaRepository<WatchingSession, UUID> {

    long countByContent(Content content);

    @org.springframework.data.jpa.repository.Query("SELECT ws.content.id, COUNT(ws) FROM WatchingSession ws WHERE ws.content.id IN :contentIds GROUP BY ws.content.id")
    List<Object[]> findWatcherCountsByContentIds(@Param("contentIds") java.util.List<UUID> contentIds);
}