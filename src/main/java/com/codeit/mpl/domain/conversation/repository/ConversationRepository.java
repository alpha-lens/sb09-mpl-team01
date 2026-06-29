package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.dto.ConversationQueryDto;
import com.codeit.mpl.domain.conversation.entity.Conversation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    @Query("SELECT c FROM Conversation c WHERE c.user1.id = :userId OR c.user2.id = :userId")
    List<Conversation> findAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT c FROM Conversation c WHERE (c.user1.id = :user1Id AND c.user2.id = :user2Id) " +
           "OR (c.user1.id = :user2Id AND c.user2.id = :user1Id)")
    Optional<Conversation> findBetweenUsers(@Param("user1Id") UUID user1Id, @Param("user2Id") UUID user2Id);

    @Query("""
    SELECT new com.codeit.mpl.domain.conversation.dto.ConversationQueryDto(
        c.id,
        CASE WHEN c.user1.id = :userId THEN c.user2.id ELSE c.user1.id END,
        CASE WHEN c.user1.id = :userId THEN c.user2.name ELSE c.user1.name END,
        CASE WHEN c.user1.id = :userId THEN c.user2.profileImageUrl ELSE c.user1.profileImageUrl END,
        dm.id,
        dm.content,
        dm.createdAt,
        (SELECT COUNT(m) FROM DirectMessage m WHERE m.conversation.id = c.id AND m.receiver.id = :userId AND m.isRead = false)
    )
    FROM Conversation c
    LEFT JOIN DirectMessage dm ON dm.conversation.id = c.id
    WHERE (c.user1.id = :userId OR c.user2.id = :userId)
      AND (dm.id IS NULL OR dm.createdAt = (SELECT MAX(m2.createdAt) FROM DirectMessage m2 WHERE m2.conversation.id = c.id))
""")
    List<ConversationQueryDto> findAllConversationsWithStats(@Param("userId") UUID userId);
}
