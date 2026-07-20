package com.codeit.mpl.domain.content.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum ContentType {

    MOVIE("movie"),
    TVSERIES("tvSeries"),
    SPORT("sport");

    private final String value;

    ContentType(
            String value
    ) {
        this.value = value;
    }

    /**
     * JSON 응답으로 직렬화할 때 사용됩니다.
     *
     * MOVIE -> "movie"
     * TVSERIES -> "tvSeries"
     * SPORT -> "sport"
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * JSON 요청을 ContentType으로 변환할 때 사용됩니다.
     *
     * 프론트 형식:
     * "movie", "tvSeries", "sport"
     *
     * 기존 대문자 형식도 호환:
     * "MOVIE", "TVSERIES", "SPORT"
     */
    @JsonCreator
    public static ContentType fromValue(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    "콘텐츠 타입은 필수입니다."
            );
        }

        return Arrays.stream(
                        values()
                )
                .filter(
                        type ->
                                type.value.equalsIgnoreCase(
                                        value
                                )
                                        || type.name().equalsIgnoreCase(
                                        value
                                )
                )
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "지원하지 않는 콘텐츠 타입입니다: "
                                                + value
                                )
                );
    }
}