package com.codeit.mpl.domain.content.mapper;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ContentMapper {

    @Autowired
    protected BinaryContentStorage binaryContentStorage;

    @Mapping(target = "id", source = "content.id")
    @Mapping(target = "type", source = "content.type")
    @Mapping(target = "title", source = "content.title")
    @Mapping(
            target = "description",
            source = "content.description"
    )
    @Mapping(
            target = "thumbnailUrl",
            source = "content.thumbnailUrl",
            qualifiedByName = "resolveThumbnailUrl"
    )
    @Mapping(target = "tags", source = "content.tags")
    @Mapping(
            target = "averageRating",
            source = "averageRating"
    )
    @Mapping(
            target = "reviewCount",
            source = "reviewCount"
    )
    @Mapping(
            target = "watcherCount",
            source = "watcherCount"
    )
    public abstract ContentDto toDto(
            Content content,
            Double averageRating,
            Integer reviewCount,
            Long watcherCount
    );

    @Mapping(target = "id", source = "content.id")
    @Mapping(target = "type", source = "content.type")
    @Mapping(target = "title", source = "content.title")
    @Mapping(
            target = "description",
            source = "content.description"
    )
    @Mapping(
            target = "thumbnailUrl",
            source = "content.thumbnailUrl",
            qualifiedByName = "resolveThumbnailUrl"
    )
    @Mapping(target = "tags", source = "content.tags")
    @Mapping(
            target = "averageRating",
            source = "averageRating"
    )
    @Mapping(
            target = "reviewCount",
            source = "reviewCount"
    )
    @Mapping(
            target = "watcherCount",
            source = "watcherCount"
    )
    public abstract ContentSummary toSummary(
            Content content,
            Double averageRating,
            Integer reviewCount,
            Long watcherCount
    );

    @Named("resolveThumbnailUrl")
    protected String resolveThumbnailUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://") || rawUrl.startsWith("/uploads/")) {
            return rawUrl;
        }
        return binaryContentStorage.getUrl(rawUrl);
    }
}