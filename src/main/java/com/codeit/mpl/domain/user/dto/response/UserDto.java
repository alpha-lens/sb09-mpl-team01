package com.codeit.mpl.domain.user.dto.response;

import com.codeit.mpl.domain.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        Instant createdAt,
        String email,
        String name,
        String profileImageUrl,
        UserRole role,
        boolean locked
) {}
