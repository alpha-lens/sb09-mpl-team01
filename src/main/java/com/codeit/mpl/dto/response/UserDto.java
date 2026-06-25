package com.codeit.mpl.dto.response;

import com.codeit.mpl.entity.UserEntity;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class UserDto {

    private UUID id;
    private Instant createdAt;
    private String email;
    private String name;
    private String profileImageUrl;
    private UserEntity.Role role;
    private boolean locked;
}
