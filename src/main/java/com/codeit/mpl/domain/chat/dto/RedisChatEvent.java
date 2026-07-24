package com.codeit.mpl.domain.chat.dto;

import java.util.UUID;

public record RedisChatEvent(
    String eventType,
    String destination,
    UUID receiverId,
    Object payload
) {}
