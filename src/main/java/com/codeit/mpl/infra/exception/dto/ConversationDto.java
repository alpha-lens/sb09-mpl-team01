package com.codeit.mpl.infra.exception.dto;

import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.user.dto.UserSummary;
import java.util.UUID;

public record ConversationDto(
    UUID id, UserSummary with, DirectMessageDto lastestMessage, boolean hasUnread
) {

}
