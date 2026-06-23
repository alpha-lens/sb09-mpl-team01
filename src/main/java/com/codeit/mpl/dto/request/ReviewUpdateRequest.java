package com.codeit.mpl.dto.request;

public record ReviewUpdateRequest(
    String text,
    Double rating

) {}