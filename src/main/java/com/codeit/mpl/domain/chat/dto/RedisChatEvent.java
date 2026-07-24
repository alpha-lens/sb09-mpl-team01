package com.codeit.mpl.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedisChatEvent(
    String eventType,
    String destination,
    UUID senderId,
    UUID receiverId,
    Object payload
) {}
