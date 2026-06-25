package com.codeit.mpl.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserLockUpdateRequest {

    @NotNull
    private Boolean locked;
}
