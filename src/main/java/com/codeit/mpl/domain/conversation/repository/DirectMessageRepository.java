package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID>, DirectMessageCustomRepository {

    long countByConversationIdAndIsReadFalseAndReceiverId(UUID conversationId, UUID receiverId);

    DirectMessage findFirstByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    Optional<DirectMessage> findByIdAndConversationIdAndReceiverId(UUID id, UUID conversationId, UUID receiverId);
    List<DirectMessage> findByConversationIdAndReceiverIdAndIsReadFalse(UUID conversationId, UUID receiverId);
}
