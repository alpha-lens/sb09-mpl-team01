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
    FORBIDDEN(HttpStatus.FORBIDDEN, "ForbiddenException", "접근 권한이 없습니다."),

    // 리뷰
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "ReviewNotFoundException", "존재하지 않는 리뷰입니다."),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "ReviewAlreadyExistsException", "이미 리뷰를 작성했습니다."),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "ReviewForbiddenException", "리뷰 작성자만 수정/삭제할 수 있습니다."),
    INVALID_REVIEW_CURSOR(HttpStatus.BAD_REQUEST, "InvalidReviewCursorException", "리뷰 커서 값이 올바르지 않습니다."),
    INVALID_REVIEW_SORT(HttpStatus.BAD_REQUEST, "InvalidReviewSortException", "리뷰 정렬 기준이 올바르지 않습니다."),

    // 플레이리스트
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PlaylistNotFoundException", "존재하지 않는 플레이리스트입니다."),
    PLAYLIST_FORBIDDEN(HttpStatus.FORBIDDEN, "PlaylistForbiddenException", "플레이리스트 소유자만 수행할 수 있습니다."),
    PLAYLIST_CONTENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "PlaylistContentAlreadyExistsException", "이미 추가된 콘텐츠입니다."),
    PLAYLIST_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PlaylistContentNotFoundException", "플레이리스트에 없는 콘텐츠입니다."),
    PLAYLIST_SUBSCRIPTION_ALREADY_EXISTS(HttpStatus.CONFLICT, "PlaylistSubscriptionAlreadyExistsException", "이미 구독중인 플레이리스트입니다."),
    PLAYLIST_SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "PlaylistSubscriptionNotFoundException", "구독하지 않은 플레이리스트입니다."),

    // 콘텐츠
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ContentNotFoundException", "존재하지 않는 콘텐츠입니다.");

    private final HttpStatus httpStatus;
    private final String exceptionName;
    private final String message;
}
