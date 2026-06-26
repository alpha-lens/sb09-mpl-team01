package com.codeit.mpl.domain.review.dto;

import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
    UUID id,
    UUID contentId,
    UUID authorId,
    String text,
    Double rating,
    Instant createdAt,
    Instant updatedAt
) {}


