package com.codeit.mpl.domain.user.dto.response;

import java.util.UUID;

public record UserSummary(
        UUID userId,
        String name,
        String profileImageUrl
) {}
