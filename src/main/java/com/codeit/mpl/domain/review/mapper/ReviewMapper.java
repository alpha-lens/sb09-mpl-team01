package com.codeit.mpl.domain.review.mapper;

import com.codeit.mpl.domain.review.dto.ReviewDto;
import com.codeit.mpl.domain.review.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

  public ReviewDto toDto(Review review) {
    return new ReviewDto(
        review.getId(),
        review.getContent().getId(),
        review.getAuthor().getId(),
        review.getText(),
        review.getRating(),
        review.getCreatedAt(),
        review.getUpdatedAt()
    );
  }
}