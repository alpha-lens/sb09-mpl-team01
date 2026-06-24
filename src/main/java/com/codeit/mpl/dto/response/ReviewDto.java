package com.codeit.mpl.dto.response;

import com.codeit.mpl.entity.ReviewEntity;
import java.time.Instant;
import java.util.UUID;

public record ReviewDto (

    UUID id,
    UUID contentId,
    UUID authorId,
    String text,
    Double rating,
    Instant createdAt,
    Instant updatedAt
) {
    public static ReviewDto from(ReviewEntity review) {
        return new ReviewDto(
            review.getId(),
            review.getContentId(),
            review.getAuthorId(),
            review.getText(),
            review.getRating(),
            review.getCreatedAt(),
            review.getUpdatedAt()
        );
    }
}


