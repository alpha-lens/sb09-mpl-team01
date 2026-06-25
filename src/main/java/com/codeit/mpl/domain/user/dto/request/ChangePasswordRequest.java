package com.codeit.mpl.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangePasswordRequest {

    @NotBlank
    @Size(min = 8, max = 255)
    private String password;
}
