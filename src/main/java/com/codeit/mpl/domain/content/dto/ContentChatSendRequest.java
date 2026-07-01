package com.codeit.mpl.domain.content.dto;

import jakarta.validation.constraints.NotBlank;

public record ContentChatSendRequest(
    @NotBlank String content
) {}
