package com.codeit.mpl.infra.exception.dto;

public record ErrorResponse(
    String exceptionName, String messages, Object details
) {

}
