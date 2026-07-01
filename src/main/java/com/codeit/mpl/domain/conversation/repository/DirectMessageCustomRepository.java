package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface DirectMessageCustomRepository {
    List<DirectMessage> findMessages(UUID conversationId, UUID idAfter, Pageable pageable);
}
