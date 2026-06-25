package com.codeit.mpl.infra.exception;

import lombok.Getter;

@Getter
public class MplException extends RuntimeException {

    private final ErrorCode errorCode;

    public MplException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
