package com.codeit.mpl.domain.conversation.dto;

import com.codeit.mpl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
    UUID id, UUID conversationId, Instant createdAt, UserSummary sender, UserSummary receiver, String content
) {

}
