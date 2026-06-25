package com.codeit.mpl.domain.user.dto.request;

import com.codeit.mpl.domain.user.entity.UserRole;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
        @NotNull UserRole role
) {}
