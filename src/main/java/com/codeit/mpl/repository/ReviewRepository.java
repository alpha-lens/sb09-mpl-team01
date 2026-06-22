package com.codeit.mpl.repository;

import com.codeit.mpl.entity.base.Review;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  boolean existsByAuthorIdAndContentId(UUID authorId, UUID contentId);
}