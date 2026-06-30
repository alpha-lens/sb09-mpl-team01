package com.codeit.mpl.domain.curating.dto.request;

import jakarta.validation.constraints.Size;

public record PlaylistUpdateRequest(
    @Size(max = 255) String title,
    String description
) {}