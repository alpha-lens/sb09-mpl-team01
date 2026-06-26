package com.codeit.mpl.domain.notification.repository;

import com.codeit.mpl.domain.notification.entity.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
    SELECT n FROM Notification n 
    WHERE n.receiver.id = :receiverId 
      AND (
        :idAfter IS NULL OR 
        n.createdAt < :idAfter OR 
        (n.createdAt = :idAfter AND n.id < :idCursor)
      )
    ORDER BY n.createdAt DESC, n.id DESC
""")
    List<Notification> findNotifications(
        @Param("receiverId") UUID receiverId,
        @Param("idAfter") Instant idAfter,
        @Param("idCursor") UUID idCursor,
        Pageable pageable
    );

    long countByReceiverId(UUID receiverId);
}
