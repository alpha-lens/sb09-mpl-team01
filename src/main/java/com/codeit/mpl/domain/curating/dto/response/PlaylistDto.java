package com.codeit.mpl.domain.curating.dto.response;

import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistDto(
    UUID id,
    UserSummary owner,
    String title,
    String description,
    Instant updatedAt,
    long subscriberCount,
    boolean subscribedByMe,
    List<ContentSummary> contents
) {}