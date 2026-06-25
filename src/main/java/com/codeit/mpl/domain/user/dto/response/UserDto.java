package com.codeit.mpl.domain.user.dto.response;

import com.codeit.mpl.domain.user.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserDto(
        UUID id,
        Instant createdAt,
        String email,
        String name,
        String profileImageUrl,
        User.Role role,
        boolean locked
) {}
