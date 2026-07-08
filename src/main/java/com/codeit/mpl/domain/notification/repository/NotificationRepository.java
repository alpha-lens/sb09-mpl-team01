package com.codeit.mpl.domain.notification.repository;

import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationCustomRepository {
    long countByReceiverId(UUID receiverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByReceiverIdAndTypeAndTargetIdAndIsReadFalse(UUID receiverId, NotificationType type, UUID targetId);

    void deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(UUID receiverId, NotificationType type, UUID targetId);
}
