package com.codeit.mpl.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RedisWatchingSessionEvent(
        String eventType, // "JOIN", "LEAVE", "SNAPSHOT"
        UUID contentId,
        String userEmail,
        String destination,
        String payloadJson
) {
}


