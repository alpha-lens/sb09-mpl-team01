package com.codeit.mpl.curating.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.MplException;
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

    playlistService.createPlaylist(ownerId, request);

    verify(playlistRepository).save(any(Playlist.class));
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

    playlistService.addContent(ownerId, playlistId, contentId);

    verify(playlistContentRepository).save(any(PlaylistContent.class));
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

    playlistService.removeContent(ownerId, playlistId, contentId);

    verify(playlistContentRepository).deleteByPlaylistAndContent(playlist, content);
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

    when(userRepository.findById(subscriberId)).thenReturn(Optional.of(subscriber));
    when(playlistRepository.findById(playlistId)).thenReturn(Optional.of(playlist));
    when(playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)).thenReturn(false);

    playlistService.subscribePlaylist(subscriberId, playlistId);

    verify(playlistSubscriptionRepository).save(any(PlaylistSubscription.class));
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
    CursorPageResponseDto<PlaylistDto> response = playlistService.getPlaylists(
        null, null, null, null, null, 10, "invalidField", Direction.DESCENDING, null
    );

    assertThat(response.data()).hasSize(1);
    assertThat(response.sortBy()).isEqualTo("createdAt");
  }
}