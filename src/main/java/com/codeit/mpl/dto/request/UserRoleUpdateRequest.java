package com.codeit.mpl.dto.request;

import com.codeit.mpl.entity.UserEntity;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRoleUpdateRequest {

    @NotNull
    private UserEntity.Role role;
}
