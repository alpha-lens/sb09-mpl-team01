package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

    @Query("SELECT dm FROM DirectMessage dm WHERE dm.conversation.id = :conversationId " +
           "AND (:idAfter IS NULL OR dm.createdAt < (SELECT cur.createdAt FROM DirectMessage cur WHERE cur.id = :idAfter)) " +
           "ORDER BY dm.createdAt DESC")
    List<DirectMessage> findMessages(
            @Param("conversationId") UUID conversationId,
            @Param("idAfter") UUID idAfter,
            Pageable pageable
    );

    long countByConversationIdAndIsReadFalseAndReceiverId(UUID conversationId, UUID receiverId);

    DirectMessage findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    Optional<DirectMessage> findByIdAndConversationIdAndReceiverId(UUID id, UUID conversationId, UUID receiverId);
    List<DirectMessage> findByConversationIdAndReceiverIdAndIsReadFalse(UUID conversationId, UUID receiverId);
}
