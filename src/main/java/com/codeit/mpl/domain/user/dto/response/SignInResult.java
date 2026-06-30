package com.codeit.mpl.domain.user.dto.response;

import com.codeit.mpl.infra.common.dto.JwtDto;

public record SignInResult(JwtDto jwtDto, String refreshToken) {}
