package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.entity.Conversation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

}
