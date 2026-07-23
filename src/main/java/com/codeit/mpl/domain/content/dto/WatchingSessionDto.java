package com.codeit.mpl.domain.content.dto;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.UUID;

public record WatchingSessionDto(
    UUID id,
    Instant createdAt,
    UserSummary watcher,
    ContentDto content
) {}
