package com.codeit.mpl.domain.review.mapper;

import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

  default ReviewDto toDto(Review review) {
    UserSummary author = new UserSummary(
        review.getAuthor().getId(),
        review.getAuthor().getName(),
        review.getAuthor().getProfileImageUrl()
    );

    return new ReviewDto(
        review.getId(),
        review.getContent().getId(),
        author,
        review.getText(),
        review.getRating(),
        review.getCreatedAt(),
        review.getUpdatedAt()
    );
  }
}