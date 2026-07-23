package com.codeit.mpl.domain.curating.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaylistCreateRequest(
    @NotBlank
    @Size(max = 255) String title,
    String description
) {}