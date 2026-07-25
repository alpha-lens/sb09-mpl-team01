package com.codeit.mpl.domain.notification.repository;

import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationCustomRepository {
    long countByReceiverId(UUID receiverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findByReceiverIdAndTypeAndTargetIdAndIsReadFalse(UUID receiverId, NotificationType type, UUID targetId);

    @Modifying(clearAutomatically = true)
    void deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(UUID receiverId, NotificationType type, UUID targetId);
}
