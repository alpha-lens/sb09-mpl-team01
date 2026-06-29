package com.codeit.mpl.domain.curating.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PlaylistDto(
    UUID id,
    UUID ownerId,
    String ownerName,
    String title,
    String description,
    Instant createdAt,
    Instant updatedAt
) {}