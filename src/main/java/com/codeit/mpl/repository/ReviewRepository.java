package com.codeit.mpl.repository;

import com.codeit.mpl.entity.ReviewEntity;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

  boolean existsByAuthorIdAndContentId(UUID authorId, UUID contentId);
  Page<ReviewEntity> findByContentId(UUID contentId, Pageable pageable);
}