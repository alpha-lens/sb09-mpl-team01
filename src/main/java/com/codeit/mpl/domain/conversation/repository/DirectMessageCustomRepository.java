package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface DirectMessageCustomRepository {
    List<DirectMessage> findMessages(UUID conversationId, UUID idAfter, Pageable pageable);
}
