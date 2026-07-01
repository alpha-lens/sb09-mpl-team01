package com.codeit.mpl.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ReviewCreateRequest(
    @NotNull UUID contentId,
    @NotBlank String text,      // ← @NotNull에서 @NotBlank로 변경
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("5.0") Double rating
) {}