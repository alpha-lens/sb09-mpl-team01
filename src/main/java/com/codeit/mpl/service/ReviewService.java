package com.codeit.mpl.service;

import com.codeit.mpl.dto.request.ReviewCreateRequest;
import com.codeit.mpl.dto.request.ReviewUpdateRequest;
import com.codeit.mpl.dto.response.ReviewDto;
import java.util.UUID;

public interface ReviewService {

  ReviewDto createReview(UUID authorId, ReviewCreateRequest request);
  ReviewDto updateReview(UUID authorId, UUID reviewId, ReviewUpdateRequest request);
  void deleteReview(UUID authorId, UUID reviewId);

}
