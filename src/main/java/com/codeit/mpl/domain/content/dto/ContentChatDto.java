package com.codeit.mpl.domain.content.dto;

import com.codeit.mpl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record ContentChatDto(
    UUID id,
    UUID contentId,
    UserSummary sender,
    String message,
    Instant createdAt
) {}
