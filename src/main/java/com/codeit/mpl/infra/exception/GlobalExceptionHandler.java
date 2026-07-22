package com.codeit.mpl.infra.exception;

import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException e) {
        log.trace("Async request / SSE stream closed by client: {}", e.getMessage());
    }

    @ExceptionHandler(MplException.class)
    public ResponseEntity<ErrorResponse> handleMplException(MplException e) {
        ErrorCode errorCode = e.getErrorCode();
        // 요청 본문(비밀번호 등 민감정보)은 남기지 않고, 어떤 에러코드가 왜 발생했는지만 추적한다.
        log.warn("Business exception occurred: {} - {}", errorCode, e.getMessage());
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "유효하지 않은 값입니다.",
                        (first, second) -> first
                ));
        log.warn("Validation failed: {}", details.keySet());
        ErrorResponse response = new ErrorResponse("ValidationException", "입력값이 올바르지 않습니다.", details);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(org.springframework.web.HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported Media Type: {}", e.getMessage());
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse("UnsupportedMediaType", "지원하지 않는 Content-Type 요청입니다. application/json 형식으로 요청해 주세요.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled exception occurred: ", e);
        return ResponseEntity
                .internalServerError()
                .body(new ErrorResponse("InternalServerError", "서버 오류가 발생했습니다.", null));
    }
}
