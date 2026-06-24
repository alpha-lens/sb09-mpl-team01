package com.codeit.mpl.repository;

import com.codeit.mpl.entity.ReviewEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<ReviewEntity, UUID> {

  boolean existsByAuthorIdAndContentId(UUID authorId, UUID contentId);
  List<ReviewEntity> findByContentId(UUID contentId);
}