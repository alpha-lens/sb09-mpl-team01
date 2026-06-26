package com.codeit.mpl.domain.conversation.dto;

import jakarta.validation.constraints.NotBlank;

public record DirectMessageSendRequest(
    @NotBlank String content
) {}
