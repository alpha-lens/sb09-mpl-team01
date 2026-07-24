package com.codeit.mpl.domain.conversation.dto;

import com.codeit.mpl.domain.user.dto.UserSummary;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ConversationDto(
    @NotNull UUID id,
    @NotNull UserSummary with,
    DirectMessageDto lastestMessage,
    boolean hasUnread
) {

}

