package com.codeit.mpl.domain.content.mapper;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.entity.Content;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ContentMapper {

    @Mapping(target = "id", source = "content.id")
    @Mapping(target = "type", source = "content.type")
    @Mapping(target = "title", source = "content.title")
    @Mapping(target = "description", source = "content.description")
    @Mapping(target = "thumbnailUrl", source = "content.thumbnailUrl")
    @Mapping(target = "tags", source = "content.tags")
    @Mapping(target = "averageRating", source = "averageRating")
    @Mapping(target = "reviewCount", source = "reviewCount")
    @Mapping(target = "watcherCount", source = "watcherCount")
    ContentDto toDto(
            Content content,
            Double averageRating,
            Integer reviewCount,
            Long watcherCount
    );

    @Mapping(target = "id", source = "content.id")
    @Mapping(target = "type", source = "content.type")
    @Mapping(target = "title", source = "content.title")
    @Mapping(target = "description", source = "content.description")
    @Mapping(target = "thumbnailUrl", source = "content.thumbnailUrl")
    @Mapping(target = "tags", source = "content.tags")
    @Mapping(target = "averageRating", source = "averageRating")
    @Mapping(target = "reviewCount", source = "reviewCount")
    ContentSummary toSummary(
            Content content,
            Double averageRating,
            Integer reviewCount
    );
}