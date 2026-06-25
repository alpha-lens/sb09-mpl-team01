package com.codeit.mpl.domain.content.dto.request;

import com.codeit.mpl.domain.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ContentCreateRequest(
        @NotNull
        ContentType type,

        @NotBlank
        String title,

        String description,

        List<String> tags
) {
}