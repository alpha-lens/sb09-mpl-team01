package com.codeit.mpl.domain.review.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.review.dto.response.ReviewStats;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.user.entity.User;
import java.util.UUID;
import java.util.List;
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

  void deleteByContent(Content content);

  @Query("SELECT new com.codeit.mpl.domain.review.dto.response.ReviewStats(r.content.id, AVG(r.rating), COUNT(r)) FROM Review r WHERE r.content.id IN :contentIds GROUP BY r.content.id")
  List<ReviewStats> findReviewStatsByContentIds(@Param("contentIds") List<UUID> contentIds);
}