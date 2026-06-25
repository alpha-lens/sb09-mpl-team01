package com.codeit.mpl.infra.common.dto;

import com.codeit.mpl.domain.user.dto.response.UserDto;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JwtDto {

    private UserDto userDto;
    private String accessToken;
}
