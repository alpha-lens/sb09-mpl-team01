package com.codeit.mpl.domain.content.dto.request;

import com.codeit.mpl.domain.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ContentImportRequest(
        @NotBlank
        String externalId,

        @NotNull
        ContentType type
) {
}