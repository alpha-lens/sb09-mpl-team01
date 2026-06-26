package com.codeit.mpl.domain.content.mapper;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.entity.Content;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

    public ContentDto toDto(
            Content content,
            Double averageRating,
            Integer reviewCount,
            Long watcherCount
    ) {
        return new ContentDto(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getTags(),
                averageRating,
                reviewCount,
                watcherCount
        );
    }

    public ContentSummary toSummary(
            Content content,
            Double averageRating,
            Integer reviewCount
    ) {
        return new ContentSummary(
                content.getId(),
                content.getType(),
                content.getTitle(),
                content.getDescription(),
                content.getThumbnailUrl(),
                content.getTags(),
                averageRating,
                reviewCount
        );
    }
}