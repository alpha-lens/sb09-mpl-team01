package com.codeit.mpl.domain.review.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ReviewUpdateRequest(
    String text,
    @Min(0)
    @Max(5) Integer rating
) {}