package com.codeit.mpl.domain.review.dto.response;

import com.codeit.mpl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record ReviewDto(
    UUID id,
    UUID contentId,
    UserSummary author,
    String text,
    int rating,
    Instant createdAt,
    Instant updatedAt
) {}