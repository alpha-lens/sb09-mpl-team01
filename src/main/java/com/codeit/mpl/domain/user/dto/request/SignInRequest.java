package com.codeit.mpl.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignInRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;
}
