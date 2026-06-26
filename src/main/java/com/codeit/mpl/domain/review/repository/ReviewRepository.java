package com.codeit.mpl.domain.review.repository;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.review.entity.Review;
import com.codeit.mpl.domain.user.entity.User;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  boolean existsByAuthorAndContent(User author, Content content);

  Page<Review> findByContent(Content content, Pageable pageable);
}