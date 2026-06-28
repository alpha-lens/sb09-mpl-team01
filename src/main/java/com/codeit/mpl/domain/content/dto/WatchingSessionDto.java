package com.codeit.mpl.domain.content.dto;

import com.codeit.mpl.domain.user.dto.UserSummary;
import java.util.UUID;

public record WatchingSessionDto(
    UUID contentId,
    UserSummary user
) {}
