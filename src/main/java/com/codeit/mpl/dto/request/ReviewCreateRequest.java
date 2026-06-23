package com.codeit.mpl.dto.request;

import java.util.UUID;

public record ReviewCreateRequest(
    UUID contentId,
    String text,
    Double rating
) {}