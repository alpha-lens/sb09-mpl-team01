package com.codeit.mpl.domain.user.dto.request;

import com.codeit.mpl.domain.user.entity.User;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(
        @NotNull User.Role role
) {}
