package com.codeit.mpl.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class JwtDto {

    private UserDto userDto;
    private String accessToken;
}
