package com.codeit.mpl.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

public record ReviewUpdateRequest(
    String text,
    @DecimalMin("0.0")
    @DecimalMax("5.0") int rating
) {}