package com.codeit.mpl.domain.content.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ContentUpdateRequest(
        @NotBlank
        String title,

        String description,

        List<String> tags
) {
}