package com.codeit.mpl.domain.profile.repository;

import com.codeit.mpl.domain.profile.entity.Follow;
import com.codeit.mpl.domain.user.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFollowerAndFollowee(User follower, User followee);

  Optional<Follow> findByFollowerAndFollowee(User follower, User followee);

  long countByFollowee(User followee);

  long countByFollower(User follower);

  List<Follow> findByFollowee(User followee);
}