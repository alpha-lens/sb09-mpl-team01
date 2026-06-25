package com.codeit.mpl.infra.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 사용자
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "UserNotFoundException", "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EmailAlreadyExistsException", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "InvalidCredentialsException", "이메일 또는 비밀번호가 올바르지 않습니다."),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "AccountLockedException", "잠긴 계정입니다."),

    // 임시 비밀번호
    TEMPORARY_PASSWORD_EXPIRED(HttpStatus.UNAUTHORIZED, "TemporaryPasswordExpiredException", "임시 비밀번호가 만료되었습니다."),

    // 토큰
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "InvalidTokenException", "유효하지 않은 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TokenExpiredException", "만료된 토큰입니다."),

    // 인가
    FORBIDDEN(HttpStatus.FORBIDDEN, "ForbiddenException", "접근 권한이 없습니다.");

    private final HttpStatus httpStatus;
    private final String exceptionName;
    private final String message;
}
