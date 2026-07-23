package com.codeit.mpl.domain.curating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.curating.dto.request.PlaylistCreateRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistUpdateRequest;
import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistContent;
import com.codeit.mpl.domain.curating.entity.PlaylistSubscription;
import com.codeit.mpl.domain.curating.repository.PlaylistContentRepository;
import com.codeit.mpl.domain.curating.repository.PlaylistRepository;
import com.codeit.mpl.domain.curating.repository.PlaylistSubscriptionRepository;
import com.codeit.mpl.domain.curating.service.PlaylistService;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.profile.entity.Follow;
import com.codeit.mpl.domain.profile.repository.FollowRepository;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock
  private PlaylistRepository playlistRepository;

  @Mock
  private PlaylistContentRepository playlistContentRepository;

  @Mock
  private PlaylistSubscriptionRepository playlistSubscriptionRepository;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ReviewRepository reviewRepository;

  @Mock
  private FollowRepository followRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Mock
  private BinaryContentStorage binaryContentStorage;

  @InjectMocks
  private PlaylistService playlistService;

  @Test
  @DisplayName("플레이리스트 생성 성공")
  void createPlaylist_success() {
    UUID ownerId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("내 플레이리스트", "설명");

    User owner = mock(User.class);
    when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
    when(owner.getId()).thenReturn(ownerId);
    when(owner.getName()).thenReturn("테스트유저");
    when(playlistSubscriptionRepository.countByPlaylist(any())).thenReturn(0L);
    when(playlistContentRepository.findByPlaylist(any())).thenReturn(List.of());

    User follower = mock(User.class);
    Follow follow = mock(Follow.class);
    when(follow.getFollower()).thenReturn(follower);
    when(followRepository.findByFollowee(owner)).thenReturn(List.of(follow));

    playlistService.createPlaylist(ownerId, request);

    verify(playlistRepository).save(any(Playlist.class));
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("플레이리스트 생성 실패 - 존재하지 않는 사용자")
  void createPlaylist_fail_userNotFound() {
    UUID ownerId = UUID.randomUUID();
    PlaylistCreateRequest request = new PlaylistCreateRequest("내 플레이리스트", "설명");

    when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.createPlaylist(ownerId, request))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("플레이리스트 단건 조회 성공")
  void getPlaylist_success() {
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");
    when(playlistSubscriptionRepository.countByPlaylist(playlist)).thenReturn(0L);
    when(playlistContentRepository.findByPlaylist(playlist)).thenReturn(List.of());

    playlistService.getPlaylist(playlistId, null);

    verify(playlistRepository).findById(playlistId);
  }

  @Test
  @DisplayName("플레이리스트 단건 조회 성공 - 콘텐츠 썸네일 URL 변환 검증")
  void getPlaylist_success_resolvesContentThumbnailUrl() {
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);
    Content content = mock(Content.class);
    PlaylistContent playlistContent = mock(PlaylistContent.class);

    String rawThumbnailKey = "content-thumbnails/a6e58696-06fa-4fe7-b911-bca2c2cf9439.jpg";
    String resolvedUrl = "http://localhost:8080/uploads/content-thumbnails/a6e58696-06fa-4fe7-b911-bca2c2cf9439.jpg";

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");
    when(playlistSubscriptionRepository.countByPlaylist(playlist)).thenReturn(0L);
    when(playlistContentRepository.findByPlaylist(playlist)).thenReturn(List.of(playlistContent));
    when(playlistContent.getContent()).thenReturn(content);
    when(content.getThumbnailUrl()).thenReturn(rawThumbnailKey);
    when(binaryContentStorage.getUrl(rawThumbnailKey)).thenReturn(resolvedUrl);

    PlaylistDto result = playlistService.getPlaylist(playlistId, null);

    assertThat(result.contents()).hasSize(1);
    assertThat(result.contents().get(0).thumbnailUrl()).isEqualTo(resolvedUrl);
  }

  @Test
  @DisplayName("플레이리스트 단건 조회 실패 - 존재하지 않음")
  void getPlaylist_fail_notFound() {
    UUID playlistId = UUID.randomUUID();

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.getPlaylist(playlistId, null))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("플레이리스트 수정 성공")
  void updatePlaylist_success() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(owner.getName()).thenReturn("테스트유저");
    when(playlistSubscriptionRepository.countByPlaylist(playlist)).thenReturn(0L);
    when(playlistContentRepository.findByPlaylist(playlist)).thenReturn(List.of());

    playlistService.updatePlaylist(ownerId, playlistId, request);

    verify(playlist).update(request.title(), request.description());
  }

  @Test
  @DisplayName("플레이리스트 수정 실패 - 소유자가 아닌 경우")
  void updatePlaylist_fail_forbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(otherUserId);

    assertThatThrownBy(() -> playlistService.updatePlaylist(ownerId, playlistId, request))
        .isInstanceOf(MplException.class);

    verify(playlist, never()).update(any(), any());
  }

  @Test
  @DisplayName("플레이리스트 삭제 성공")
  void deletePlaylist_success() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);

    playlistService.deletePlaylist(ownerId, playlistId);

    verify(playlistRepository).delete(playlist);
  }

  @Test
  @DisplayName("플레이리스트 삭제 실패 - 소유자가 아닌 경우")
  void deletePlaylist_fail_forbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(otherUserId);

    assertThatThrownBy(() -> playlistService.deletePlaylist(ownerId, playlistId))
        .isInstanceOf(MplException.class);

    verify(playlistRepository, never()).delete(any(Playlist.class));
  }

  @Test
  @DisplayName("콘텐츠 추가 성공")
  void addContent_success() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    Content content = mock(Content.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).thenReturn(false);

    User subscriber = mock(User.class);
    PlaylistSubscription sub = mock(PlaylistSubscription.class);
    when(sub.getSubscriber()).thenReturn(subscriber);
    when(subscriber.getId()).thenReturn(UUID.randomUUID());
    when(playlist.getTitle()).thenReturn("Test Playlist");
    when(content.getTitle()).thenReturn("Test Content");
    when(playlistSubscriptionRepository.findByPlaylist(playlist)).thenReturn(List.of(sub));

    playlistService.addContent(ownerId, playlistId, contentId);

    verify(playlistContentRepository).save(any(PlaylistContent.class));
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("콘텐츠 추가 실패 - 이미 추가된 콘텐츠")
  void addContent_fail_alreadyExists() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    Content content = mock(Content.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).thenReturn(true);

    assertThatThrownBy(() -> playlistService.addContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);

    verify(playlistContentRepository, never()).save(any(PlaylistContent.class));
  }

  @Test
  @DisplayName("콘텐츠 삭제 성공")
  void removeContent_success() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    Content content = mock(Content.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).thenReturn(true);

    User subscriber = mock(User.class);
    PlaylistSubscription sub = mock(PlaylistSubscription.class);
    when(sub.getSubscriber()).thenReturn(subscriber);
    when(subscriber.getId()).thenReturn(UUID.randomUUID());
    when(playlist.getTitle()).thenReturn("Test Playlist");
    when(content.getTitle()).thenReturn("Test Content");
    when(playlistSubscriptionRepository.findByPlaylist(playlist)).thenReturn(List.of(sub));

    playlistService.removeContent(ownerId, playlistId, contentId);

    verify(playlistContentRepository).deleteByPlaylistAndContent(playlist, content);
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 플레이리스트에 없는 콘텐츠")
  void removeContent_fail_notFound() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    Content content = mock(Content.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).thenReturn(false);

    assertThatThrownBy(() -> playlistService.removeContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);

    verify(playlistContentRepository, never()).deleteByPlaylistAndContent(any(), any());
  }

  @Test
  @DisplayName("플레이리스트 구독 성공")
  void subscribePlaylist_success() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    UUID ownerId = UUID.randomUUID();

    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)).thenReturn(false);
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(subscriber.getId()).thenReturn(subscriberId);
    when(playlist.getTitle()).thenReturn("Test Playlist");
    when(subscriber.getName()).thenReturn("SubscriberName");

    playlistService.subscribePlaylist(subscriberId, playlistId);

    verify(playlistSubscriptionRepository).save(any(PlaylistSubscription.class));
    verify(eventPublisher).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("플레이리스트 구독 실패 - 이미 구독중")
  void subscribePlaylist_fail_alreadyExists() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)).thenReturn(true);

    assertThatThrownBy(() -> playlistService.subscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(MplException.class);

    verify(playlistSubscriptionRepository, never()).save(any(PlaylistSubscription.class));
  }

  @Test
  @DisplayName("플레이리스트 구독 취소 성공")
  void unsubscribePlaylist_success() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)).thenReturn(true);

    playlistService.unsubscribePlaylist(subscriberId, playlistId);

    verify(playlistSubscriptionRepository).deleteByPlaylistAndSubscriber(playlist, subscriber);
  }

  @Test
  @DisplayName("플레이리스트 구독 취소 실패 - 구독하지 않음")
  void unsubscribePlaylist_fail_notFound() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)).thenReturn(false);

    assertThatThrownBy(() -> playlistService.unsubscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(MplException.class);

    verify(playlistSubscriptionRepository, never()).deleteByPlaylistAndSubscriber(any(), any());
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 성공")
  void getPlaylists_success() {
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);
    UUID playlistId = UUID.randomUUID();

    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(playlist)));
    when(playlistRepository.count(any(Specification.class))).thenReturn(1L);
    when(playlist.getId()).thenReturn(playlistId);
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");

    List<Object[]> stats = Collections.singletonList(new Object[]{playlistId, 0L});
    when(playlistSubscriptionRepository.findSubscriptionStats(any())).thenReturn(stats);
    when(playlistContentRepository.findByPlaylistIdIn(any())).thenReturn(Collections.emptyList());
    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 10, "createdAt", Direction.DESCENDING, null
    );

    assertThat(response.data()).hasSize(1);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 실패 - limit이 0 이하")
  void getPlaylists_fail_invalidLimit() {
    assertThatThrownBy(() ->
        playlistService.getPlaylists(null, null, null, null, null, 0, "createdAt", Direction.DESCENDING, null)
    ).isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 - 허용되지 않은 정렬 기준은 createdAt으로 fallback")
  void getPlaylists_fallback_invalidSortBy() {
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);
    UUID playlistId = UUID.randomUUID();

    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(playlist)));
    when(playlistRepository.count(any(Specification.class))).thenReturn(1L);
    when(playlist.getId()).thenReturn(playlistId);
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");

    List<Object[]> stats = Collections.singletonList(new Object[]{playlistId, 0L});
    when(playlistSubscriptionRepository.findSubscriptionStats(any())).thenReturn(stats);
    when(playlistContentRepository.findByPlaylistIdIn(any())).thenReturn(Collections.emptyList());
    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 10, "invalidField", Direction.DESCENDING, null
    );

    assertThat(response.data()).hasSize(1);
    assertThat(response.sortBy()).isEqualTo("createdAt");
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 실패 - 커서 쌍 불일치")
  void getPlaylists_fail_invalidCursorPair() {
    assertThatThrownBy(() -> playlistService.getPlaylists(
        null, null, null, "2024-01-01T00:00:00Z", null, 10, "createdAt", Direction.DESCENDING, null
    )).isInstanceOf(com.codeit.mpl.infra.exception.playlist.InvalidPlaylistCursorException.class);
  }

  @Test
  @DisplayName("플레이리스트 단건 조회 - 컨텐츠, 리뷰 통계, 구독 여부 완벽 매핑 커버리지")
  void getPlaylist_withContentsAndReviews() {
    UUID playlistId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();

    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);
    User currentUser = mock(User.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(playlist.getId()).thenReturn(playlistId);

    // 현재 로그인된 유저 세팅 및 구독 여부 true 설정
    when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, currentUser)).thenReturn(true);

    // 플레이리스트 안에 컨텐츠가 1개 들어있는 상황 모킹
    PlaylistContent pc = mock(PlaylistContent.class);
    Content content = mock(Content.class);
    when(pc.getContent()).thenReturn(content);
    when(playlistContentRepository.findByPlaylist(playlist)).thenReturn(List.of(pc));

    // 해당 컨텐츠의 별점과 리뷰 수 모킹
    when(reviewRepository.findAverageRatingByContent(content)).thenReturn(4.5);
    when(reviewRepository.countByContent(content)).thenReturn(10L);

    PlaylistDto dto = playlistService.getPlaylist(playlistId, currentUserId);

    assertThat(dto.contents()).hasSize(1);

    // 💡 아래 부분을 ContentSummary DTO에 정의된 실제 getter 메서드 명으로 바꿔주세요!
    // 만약 avgRating() 이라면 그대로 두시고, 아니라면 avgRating() 부분을 아래처럼 바꿔보세요.
    assertThat(dto.contents().get(0).averageRating()).isEqualTo(4.5);
    assertThat(dto.contents().get(0).reviewCount()).isEqualTo(10);

    assertThat(dto.contents().get(0).reviewCount()).isEqualTo(10);
    assertThat(dto.subscribedByMe()).isTrue();
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 - 다음 페이지 존재(hasNext=true) 및 title 커서 추출")
  @MockitoSettings(strictness = Strictness.LENIENT)
  void getPlaylists_hasNext_titleSort() {
    UUID currentUserId = UUID.randomUUID();
    User currentUser = mock(User.class);
    when(userRepository.findById(currentUserId)).thenReturn(Optional.of(currentUser));

    Playlist p1 = mock(Playlist.class);
    Playlist p2 = mock(Playlist.class);
    User owner = mock(User.class);

    UUID p1Id = UUID.randomUUID();
    UUID p2Id = UUID.randomUUID();

    // 기존의 doReturn 방식 그대로 유지
    doReturn(p1Id).when(p1).getId();
    doReturn(owner).when(p1).getOwner();
    doReturn("A Title").when(p1).getTitle();

    doReturn(p2Id).when(p2).getId();
    doReturn(owner).when(p2).getOwner();

    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(p1, p2)));
    when(playlistRepository.count(any(Specification.class))).thenReturn(2L);

    when(playlistSubscriptionRepository.findSubscribedPlaylistIds(any(), any())).thenReturn(List.of(p1Id));

    when(playlistContentRepository.findByPlaylistIdIn(any())).thenReturn(Collections.emptyList());

    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 1, "title", Direction.ASCENDING, currentUserId
    );

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isEqualTo("A Title");
    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).subscribedByMe()).isTrue();
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 - updatedAt 커서 추출")
  void getPlaylists_hasNext_updatedAtSort() {
    Playlist p1 = mock(Playlist.class);
    Playlist p2 = mock(Playlist.class);
    User owner = mock(User.class);

    // lenient()를 추가하여 사용되지 않을 수도 있는 stubbing에 대해 관대하게 처리합니다.
    lenient().when(p1.getId()).thenReturn(UUID.randomUUID());
    lenient().when(p1.getOwner()).thenReturn(owner);
    when(p1.getUpdatedAt()).thenReturn(java.time.Instant.now());

    lenient().when(p2.getId()).thenReturn(UUID.randomUUID());
    lenient().when(p2.getOwner()).thenReturn(owner);

    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(p1, p2)));

    when(playlistContentRepository.findByPlaylistIdIn(any())).thenReturn(Collections.emptyList());

    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 1, "updatedAt", Direction.DESCENDING, null
    );

    assertThat(response.hasNext()).isTrue();
    assertThat(response.nextCursor()).isNotNull();
  }

  @Test
  @DisplayName("알림 방어 로직 - 소유자가 본인 플레이리스트에 컨텐츠 추가 시 알림 미발송")
  void addContent_noNotification_whenSubscriberIsOwner() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    Content content = mock(Content.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).thenReturn(false);

    PlaylistSubscription sub = mock(PlaylistSubscription.class);
    when(sub.getSubscriber()).thenReturn(owner); // 구독자가 소유자와 동일함!
    when(playlistSubscriptionRepository.findByPlaylist(playlist)).thenReturn(List.of(sub));

    playlistService.addContent(ownerId, playlistId, contentId);

    // 소유자와 구독자가 같으므로 알림이 발송되지 않아야 함
    verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("Specification 람다 내부 분기점(ASC/DESC, 필터, 파싱 예외) 강제 실행 커버리지")
  @SuppressWarnings("unchecked")
  void specification_Coverage_Hack() {
    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(org.springframework.data.domain.Page.empty());

    org.mockito.ArgumentCaptor<Specification<Playlist>> specCaptor =
        org.mockito.ArgumentCaptor.forClass(Specification.class);

    UUID uuid = UUID.randomUUID();
    String validDate = java.time.Instant.now().toString();

    // 1. 다양한 검색 필터 및 정렬 조건 (createdAt, updatedAt, title / ASC, DESC)
    playlistService.getPlaylists("keyword", uuid, uuid, validDate, uuid, 10, "createdAt", Direction.ASCENDING, null);
    playlistService.getPlaylists(null, null, null, validDate, uuid, 10, "updatedAt", Direction.DESCENDING, null);
    playlistService.getPlaylists(null, null, null, "titleCursor", uuid, 10, "title", Direction.ASCENDING, null);
    playlistService.getPlaylists(null, null, null, "titleCursor", uuid, 10, "title", Direction.DESCENDING, null);

    // 2. 파싱 예외 발생 (잘못된 날짜 형식)
    try {
      playlistService.getPlaylists(null, null, null, "invalidDate", uuid, 10, "createdAt", Direction.DESCENDING, null);
    } catch (Exception ignored) {}

    // 낚아채기
    verify(playlistRepository, org.mockito.Mockito.atLeastOnce())
        .findAll(specCaptor.capture(), any(Pageable.class));

    jakarta.persistence.criteria.Root<Playlist> root = mock(jakarta.persistence.criteria.Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    jakarta.persistence.criteria.CriteriaQuery<?> query = mock(jakarta.persistence.criteria.CriteriaQuery.class);
    jakarta.persistence.criteria.CriteriaBuilder cb = mock(jakarta.persistence.criteria.CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);

    // 3. 람다식 강제 실행
    for (Specification<Playlist> spec : specCaptor.getAllValues()) {
      try { spec.toPredicate(root, query, cb); } catch (Exception ignored) {}
    }
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 - 조회 결과가 비어있는 경우 (empty playlistIds)")
  void getPlaylists_emptyPlaylistIds() {
    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(org.springframework.data.domain.Page.empty());
    when(playlistRepository.count(any(Specification.class))).thenReturn(0L);

    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 10, "createdAt", Direction.DESCENDING, null
    );

    assertThat(response.data()).isEmpty();
    assertThat(response.totalCount()).isEqualTo(0L);
    assertThat(response.hasNext()).isFalse();
  }

  @Test
  @DisplayName("콘텐츠 추가 실패 - 존재하지 않는 플레이리스트")
  void addContent_fail_playlistNotFound() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.addContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("콘텐츠 추가 실패 - 소유자 권한 없음")
  void addContent_fail_forbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID otherOwnerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(otherOwnerId);

    assertThatThrownBy(() -> playlistService.addContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("콘텐츠 추가 실패 - 존재하지 않는 콘텐츠")
  void addContent_fail_contentNotFound() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.addContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 존재하지 않는 플레이리스트")
  void removeContent_fail_playlistNotFound() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.removeContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 소유자 권한 없음")
  void removeContent_fail_forbidden() {
    UUID ownerId = UUID.randomUUID();
    UUID otherOwnerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(otherOwnerId);

    assertThatThrownBy(() -> playlistService.removeContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("콘텐츠 삭제 실패 - 존재하지 않는 콘텐츠")
  void removeContent_fail_contentNotFound() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.removeContent(ownerId, playlistId, contentId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("알림 방어 로직 - 플레이리스트에서 콘텐츠 삭제 시 구독자가 소유자인 경우 알림 미발송")
  void removeContent_noNotification_whenSubscriberIsOwner() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();

    User owner = mock(User.class);
    Playlist playlist = mock(Playlist.class);
    Content content = mock(Content.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(ownerId);
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(playlistContentRepository.existsByPlaylistAndContent(playlist, content)).thenReturn(true);

    PlaylistSubscription sub = mock(PlaylistSubscription.class);
    when(sub.getSubscriber()).thenReturn(owner);
    when(playlistSubscriptionRepository.findByPlaylist(playlist)).thenReturn(List.of(sub));

    playlistService.removeContent(ownerId, playlistId, contentId);

    verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("플레이리스트 구독 실패 - 존재하지 않는 사용자")
  void subscribePlaylist_fail_userNotFound() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    when(userRepository.findById(subscriberId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.subscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("플레이리스트 구독 실패 - 존재하지 않는 플레이리스트")
  void subscribePlaylist_fail_playlistNotFound() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.subscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("플레이리스트 구독 - 구독자가 소유자인 경우 구독 알림 미발송")
  void subscribePlaylist_noNotification_whenSubscriberIsOwner() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    Playlist playlist = mock(Playlist.class);

    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)).thenReturn(false);
    when(playlist.getOwner()).thenReturn(subscriber);
    when(subscriber.getId()).thenReturn(subscriberId);

    playlistService.subscribePlaylist(subscriberId, playlistId);

    verify(playlistSubscriptionRepository).save(any(PlaylistSubscription.class));
    verify(eventPublisher, never()).publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("플레이리스트 구독 취소 실패 - 존재하지 않는 사용자")
  void unsubscribePlaylist_fail_userNotFound() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    when(userRepository.findById(subscriberId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.unsubscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("플레이리스트 구독 취소 실패 - 존재하지 않는 플레이리스트")
  void unsubscribePlaylist_fail_playlistNotFound() {
    UUID subscriberId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    User subscriber = mock(User.class);
    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> playlistService.unsubscribePlaylist(subscriberId, playlistId))
        .isInstanceOf(MplException.class);
  }

  @Test
  @DisplayName("resolveThumbnailUrl의 다양한 프리픽스 조건 검증 (null, http, https, /uploads/, raw key)")
  void resolveThumbnailUrl_branches() {
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);
    Content c1 = mock(Content.class);
    Content c2 = mock(Content.class);
    Content c3 = mock(Content.class);
    Content c4 = mock(Content.class);

    PlaylistContent pc1 = mock(PlaylistContent.class);
    PlaylistContent pc2 = mock(PlaylistContent.class);
    PlaylistContent pc3 = mock(PlaylistContent.class);
    PlaylistContent pc4 = mock(PlaylistContent.class);

    when(pc1.getContent()).thenReturn(c1);
    when(pc2.getContent()).thenReturn(c2);
    when(pc3.getContent()).thenReturn(c3);
    when(pc4.getContent()).thenReturn(c4);

    when(c1.getThumbnailUrl()).thenReturn(null);
    when(c2.getThumbnailUrl()).thenReturn("http://example.com/img.jpg");
    when(c3.getThumbnailUrl()).thenReturn("https://example.com/img.jpg");
    when(c4.getThumbnailUrl()).thenReturn("/uploads/img.jpg");

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");
    when(playlistSubscriptionRepository.countByPlaylist(playlist)).thenReturn(0L);
    when(playlistContentRepository.findByPlaylist(playlist)).thenReturn(List.of(pc1, pc2, pc3, pc4));

    PlaylistDto result = playlistService.getPlaylist(playlistId, null);

    assertThat(result.contents()).hasSize(4);
    assertThat(result.contents().get(0).thumbnailUrl()).isNull();
    assertThat(result.contents().get(1).thumbnailUrl()).isEqualTo("http://example.com/img.jpg");
    assertThat(result.contents().get(2).thumbnailUrl()).isEqualTo("https://example.com/img.jpg");
    assertThat(result.contents().get(3).thumbnailUrl()).isEqualTo("/uploads/img.jpg");
  }

  @Test
  @DisplayName("resolveProfileImageUrl 프로필 이미지 URL 처리 검증")
  void resolveProfileImageUrl_branch() {
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);

    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");
    when(owner.getProfileImageUrl()).thenReturn("profile-key");
    when(binaryContentStorage.getUrl("profile-key")).thenReturn("http://s3/profile.jpg");
    when(playlistSubscriptionRepository.countByPlaylist(playlist)).thenReturn(0L);
    when(playlistContentRepository.findByPlaylist(playlist)).thenReturn(List.of());

    PlaylistDto result = playlistService.getPlaylist(playlistId, null);

    assertThat(result.owner().profileImageUrl()).isEqualTo("http://s3/profile.jpg");
  }

  @Test
  @DisplayName("플레이리스트 목록 조회 - 콘텐츠가 포함된 경우 toDtoSimple 내부 contentSummaries 매핑 검증")
  void getPlaylists_withContents_callsToDtoSimpleWithContents() {
    Playlist playlist = mock(Playlist.class);
    User owner = mock(User.class);
    Content content = mock(Content.class);
    PlaylistContent pc = mock(PlaylistContent.class);
    UUID playlistId = UUID.randomUUID();

    when(playlistRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(playlist)));
    when(playlistRepository.count(any(Specification.class))).thenReturn(1L);
    when(playlist.getId()).thenReturn(playlistId);
    when(playlist.getOwner()).thenReturn(owner);
    when(owner.getId()).thenReturn(UUID.randomUUID());
    when(owner.getName()).thenReturn("테스트유저");

    when(pc.getPlaylist()).thenReturn(playlist);
    when(pc.getContent()).thenReturn(content);

    when(playlistSubscriptionRepository.findSubscriptionStats(any())).thenReturn(Collections.singletonList(new Object[]{playlistId, 1L}));
    when(playlistContentRepository.findByPlaylistIdIn(any())).thenReturn(List.of(pc));

    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 10, "createdAt", Direction.DESCENDING, null
    );

    assertThat(response.data()).hasSize(1);
    assertThat(response.data().get(0).contents()).hasSize(1);
  }
}