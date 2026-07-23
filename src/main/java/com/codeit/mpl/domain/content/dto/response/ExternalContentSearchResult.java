package com.codeit.mpl.domain.content.dto.response;

import com.codeit.mpl.domain.content.entity.ContentType;

public record ExternalContentSearchResult(
        String externalId,
        ContentType type,
        String title,
        String description,
        String thumbnailUrl,
        String releaseDate
) {
}