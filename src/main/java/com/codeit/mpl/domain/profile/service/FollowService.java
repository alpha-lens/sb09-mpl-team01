package com.codeit.mpl.domain.profile.service;

import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.entity.Follow;
import com.codeit.mpl.domain.profile.repository.FollowRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.exception.follow.FollowAlreadyExistsException;
import com.codeit.mpl.infra.exception.follow.FollowForbiddenException;
import com.codeit.mpl.infra.exception.follow.FollowNotFoundException;
import com.codeit.mpl.infra.exception.follow.FollowSelfException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;

@Service
@RequiredArgsConstructor
@Transactional
public class FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final ApplicationEventPublisher eventPublisher;

  // 팔로우
  public FollowDto follow(UUID followerId, UUID followeeId) {
    if (followerId.equals(followeeId)) {
      throw new FollowSelfException();
    }

    User follower = userRepository.findById(followerId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    User followee = userRepository.findById(followeeId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    if (followRepository.existsByFollowerAndFollowee(follower, followee)) {
      throw new FollowAlreadyExistsException();
    }

    Follow follow = followRepository.save(new Follow(follower, followee));

    eventPublisher.publishEvent(new NotificationEvent(
        followee,
        follower,
        NotificationLevel.INFO,
        "팔로우 알림",
        follower.getName() + "님이 회원님을 팔로우하기 시작했습니다.",
        NotificationType.FOLLOW,
        follow.getId()
    ));

    return new FollowDto(follow.getId(), followee.getId(), follower.getId());
  }

  // 팔로우 취소 (followId로)
  public void unfollow(UUID followerId, UUID followId) {
    Follow follow = followRepository.findById(followId)
        .orElseThrow(FollowNotFoundException::new);

    if (!follow.getFollower().getId().equals(followerId)) {
      throw new FollowForbiddenException();
    }

    followRepository.delete(follow);
  }

  // 팔로우 여부 조회
  @Transactional(readOnly = true)
  public FollowDto getFollowedByMe(UUID followerId, UUID followeeId) {
    if (followerId.equals(followeeId)) {
      return null;
    }

    User follower = userRepository.findById(followerId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    User followee = userRepository.findById(followeeId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    return followRepository.findByFollowerAndFollowee(follower, followee)
        .map(follow -> new FollowDto(follow.getId(), followee.getId(), follower.getId()))
        .orElse(null);
  }

  // 팔로워 수 조회
  @Transactional(readOnly = true)
  public long getFollowerCount(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));
    return followRepository.countByFollowee(user);
  }
}