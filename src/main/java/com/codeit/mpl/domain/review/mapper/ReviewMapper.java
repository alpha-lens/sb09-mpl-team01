package com.codeit.mpl.domain.review.mapper;

import com.codeit.mpl.domain.review.dto.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

  @Mapping(source = "content.id", target = "contentId")
  @Mapping(source = "author.id", target = "authorId")
  ReviewDto toDto(Review review);
}