package com.codeit.mpl.infra.exception;

import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ErrorResponse {

    private final String exceptionName;
    private final String message;
    private final Map<String, String> details;

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getExceptionName(), errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, Map<String, String> details) {
        return new ErrorResponse(errorCode.getExceptionName(), errorCode.getMessage(), details);
    }
}
