package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.dto.ConversationQueryDto;
import java.util.List;
import java.util.UUID;

public interface ConversationCustomRepository {
    List<ConversationQueryDto> findAllConversationsWithStats(
        UUID userId,
        String keywordLike,
        String cursor,
        UUID idAfter,
        int limit
    );
}
