package com.codeit.mpl.domain.profile.dto.response;

import java.util.UUID;

public record FollowDto(
    UUID id,
    UUID followeeId,
    UUID followerId
) {}