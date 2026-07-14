package com.codeit.mpl.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.profile.dto.response.FollowDto;
import com.codeit.mpl.domain.profile.entity.Follow;
import com.codeit.mpl.domain.profile.repository.FollowRepository;
import com.codeit.mpl.domain.profile.service.FollowService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.exception.MplException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @Mock
  private FollowRepository followRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private FollowService followService;

  @Test
  @DisplayName("팔로우 성공")
  void follow_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = mock(User.class);
    User followee = mock(User.class);
    Follow follow = mock(Follow.class);

    when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
    when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
    when(followRepository.existsByFollowerAndFollowee(follower, followee)).thenReturn(false);
    when(followRepository.save(any(Follow.class))).thenReturn(follow);
    when(follow.getId()).thenReturn(UUID.randomUUID());
    when(followee.getId()).thenReturn(followeeId);
    when(follower.getId()).thenReturn(followerId);
    when(follower.getName()).thenReturn("테스트팔로워");

    FollowDto result = followService.follow(followerId, followeeId);

    verify(followRepository).save(any(Follow.class));
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("팔로우 실패 - 자기 자신 팔로우")
  void follow_fail_selfFollow() {
    UUID userId = UUID.randomUUID();

    assertThatThrownBy(() -> followService.follow(userId, userId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("팔로우 실패 - 이미 팔로우한 사용자")
  void follow_fail_alreadyExists() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = mock(User.class);
    User followee = mock(User.class);

    when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
    when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
    when(followRepository.existsByFollowerAndFollowee(follower, followee)).thenReturn(true);

    assertThatThrownBy(() -> followService.follow(followerId, followeeId))
        .isInstanceOf(MplException.class);

    verify(followRepository, never()).save(any(Follow.class));
  }

  @Test
  @DisplayName("팔로우 실패 - 존재하지 않는 사용자")
  void follow_fail_userNotFound() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    when(userRepository.findById(followerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> followService.follow(followerId, followeeId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("팔로우 취소 성공")
  void unfollow_success() {
    UUID followerId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    User follower = mock(User.class);
    Follow follow = mock(Follow.class);

    when(followRepository.findById(followId)).thenReturn(Optional.of(follow));
    when(follow.getFollower()).thenReturn(follower);
    when(follower.getId()).thenReturn(followerId);

    followService.unfollow(followerId, followId);

    verify(followRepository).delete(follow);
  }

  @Test
  @DisplayName("팔로우 취소 실패 - 팔로우 관계 없음")
  void unfollow_fail_notFound() {
    UUID followerId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    when(followRepository.findById(followId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> followService.unfollow(followerId, followId))
        .isInstanceOf(MplException.class);

    verify(followRepository, never()).delete(any(Follow.class));
  }

  @Test
  @DisplayName("팔로우 취소 실패 - 권한 없음")
  void unfollow_fail_forbidden() {
    UUID followerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    User follower = mock(User.class);
    Follow follow = mock(Follow.class);

    when(followRepository.findById(followId)).thenReturn(Optional.of(follow));
    when(follow.getFollower()).thenReturn(follower);
    when(follower.getId()).thenReturn(otherUserId);

    assertThatThrownBy(() -> followService.unfollow(followerId, followId))
        .isInstanceOf(MplException.class);

    verify(followRepository, never()).delete(any(Follow.class));
  }

  @Test
  @DisplayName("팔로우 여부 조회 성공 - 팔로우 중")
  void getFollowedByMe_success() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = mock(User.class);
    User followee = mock(User.class);
    Follow follow = mock(Follow.class);

    when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
    when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
    when(followRepository.findByFollowerAndFollowee(follower, followee)).thenReturn(Optional.of(follow));
    when(follow.getId()).thenReturn(UUID.randomUUID());
    when(followee.getId()).thenReturn(followeeId);
    when(follower.getId()).thenReturn(followerId);

    FollowDto result = followService.getFollowedByMe(followerId, followeeId);

    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("팔로우 여부 조회 - 팔로우하지 않음 → null 반환")
  void getFollowedByMe_notFollowing() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    User follower = mock(User.class);
    User followee = mock(User.class);

    when(userRepository.findById(followerId)).thenReturn(Optional.of(follower));
    when(userRepository.findById(followeeId)).thenReturn(Optional.of(followee));
    when(followRepository.findByFollowerAndFollowee(follower, followee)).thenReturn(Optional.empty());

    FollowDto result = followService.getFollowedByMe(followerId, followeeId);

    assertThat(result).isNull();
  }

  @Test
  @DisplayName("팔로워 수 조회 성공")
  void getFollowerCount_success() {
    UUID userId = UUID.randomUUID();
    User user = mock(User.class);

    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(followRepository.countByFollowee(user)).thenReturn(5L);

    long count = followService.getFollowerCount(userId);

    assertThat(count).isEqualTo(5L);
  }

  @Test
  @DisplayName("팔로워 수 조회 실패 - 존재하지 않는 사용자")
  void getFollowerCount_fail_userNotFound() {
    UUID userId = UUID.randomUUID();

    when(userRepository.findById(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> followService.getFollowerCount(userId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("팔로우 여부 조회 - 자기 자신 조회 → null 반환")
  void getFollowedByMe_selfFollow() {
    UUID userId = UUID.randomUUID();

    FollowDto result = followService.getFollowedByMe(userId, userId);

    assertThat(result).isNull();
  }
}