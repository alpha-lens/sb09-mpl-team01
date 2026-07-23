package com.codeit.mpl.infra.common.dto;

import com.codeit.mpl.domain.user.dto.response.UserDto;

public record JwtDto(
        UserDto userDto,
        String accessToken
) {}
