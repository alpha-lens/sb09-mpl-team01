package com.codeit.mpl.domain.review.mapper;

import com.codeit.mpl.domain.review.dto.response.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.user.dto.response.UserSummary;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ReviewMapper {

  @Autowired
  protected BinaryContentStorage binaryContentStorage;

  public ReviewDto toDto(Review review) {
    String authorImageUrl = review.getAuthor().getProfileImageUrl() != null
        ? binaryContentStorage.getUrl(review.getAuthor().getProfileImageUrl())
        : null;
    UserSummary author = new UserSummary(
        review.getAuthor().getId(),
        review.getAuthor().getName(),
        authorImageUrl
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