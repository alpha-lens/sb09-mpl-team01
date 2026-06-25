package com.codeit.mpl.domain.content.dto.response;

import com.codeit.mpl.domain.content.entity.ContentType;
import java.util.List;
import java.util.UUID;

public record ContentSummary(
        UUID id,
        ContentType type,
        String title,
        String description,
        String thumbnailUrl,
        List<String> tags,
        Double averageRating,
        Integer reviewCount
) {
}