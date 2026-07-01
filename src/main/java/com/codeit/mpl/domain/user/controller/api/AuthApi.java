package com.codeit.mpl.domain.user.controller.api;

import com.codeit.mpl.domain.user.dto.request.ResetPasswordRequest;
import com.codeit.mpl.domain.user.dto.request.SignInRequest;
import com.codeit.mpl.infra.common.dto.JwtDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@Tag(name = "인증 관리", description = "로그인·로그아웃·토큰 재발급·CSRF 토큰 API")
public interface AuthApi {

    @Operation(summary = "로그인", description = "응답 쿠키(REFRESH_TOKEN)에 refresh token이 저장됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치"),
            @ApiResponse(responseCode = "403", description = "잠긴 계정")
    })
    ResponseEntity<JwtDto> signIn(@Valid @RequestBody SignInRequest request, HttpServletResponse response);

    @Operation(summary = "로그아웃")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "로그아웃 성공"),
            @ApiResponse(responseCode = "401", description = "인증 필요")
    })
    ResponseEntity<Void> signOut(UserDetails userDetails, HttpServletResponse response);

    @Operation(summary = "비밀번호 초기화", description = "임시 비밀번호를 발급합니다. 유효 시간 3분.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "임시 비밀번호 발급 성공"),
            @ApiResponse(responseCode = "400", description = "입력값 검증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request);

    @Operation(summary = "토큰 재발급", description = "쿠키(REFRESH_TOKEN)로 새 access token과 refresh token을 발급합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "재발급 성공"),
            @ApiResponse(responseCode = "401", description = "유효하지 않은 refresh token")
    })
    ResponseEntity<JwtDto> refresh(String refreshToken, HttpServletResponse response);

    @Operation(summary = "CSRF 토큰 조회", description = "응답 body와 쿠키(XSRF-TOKEN) 모두에 CSRF 토큰이 반환됩니다.")
    @ApiResponse(responseCode = "200", description = "CSRF 토큰 발급 성공")
    ResponseEntity<Map<String, String>> csrfToken(CsrfToken csrfToken);
}
