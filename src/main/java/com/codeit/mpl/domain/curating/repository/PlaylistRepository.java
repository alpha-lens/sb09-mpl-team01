package com.codeit.mpl.domain.curating.repository;

import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID>,
    JpaSpecificationExecutor<Playlist> {

  // --- 코드레빗 리뷰 반영: owner 데이터를 한 번에 가져오도록(N+1 방지) 오버라이드 ---
  @Override
  @EntityGraph(attributePaths = {"owner"})
  Page<Playlist> findAll(Specification<Playlist> spec, Pageable pageable);

  boolean existsByOwnerAndId(User owner, UUID id);
}