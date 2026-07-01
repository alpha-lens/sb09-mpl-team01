package com.codeit.mpl.domain.review.repository;

import com.codeit.mpl.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.user.entity.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, UUID>,
    JpaSpecificationExecutor<Review> {

  boolean existsByAuthorAndContent(User author, Content content);

  Page<Review> findByContent(Content content, Pageable pageable);

  @Query("SELECT AVG(r.rating) FROM Review r WHERE r.content = :content")
  Double findAverageRatingByContent(@Param("content") Content content);

  long countByContent(Content content);
}