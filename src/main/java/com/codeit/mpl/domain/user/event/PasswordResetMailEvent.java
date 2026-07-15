package com.codeit.mpl.domain.user.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PasswordResetMailEvent {
    private final String email;
    private final String tempPassword;
}
