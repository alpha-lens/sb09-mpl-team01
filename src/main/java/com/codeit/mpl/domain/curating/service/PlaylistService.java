package com.codeit.mpl.domain.curating.service;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.curating.dto.request.PlaylistCreateRequest;
import com.codeit.mpl.domain.curating.dto.request.PlaylistUpdateRequest;
import com.codeit.mpl.domain.curating.dto.response.PlaylistDto;
import com.codeit.mpl.domain.curating.entity.Playlist;
import com.codeit.mpl.domain.curating.entity.PlaylistContent;
import com.codeit.mpl.domain.curating.entity.PlaylistSubscription;
import com.codeit.mpl.domain.curating.mapper.PlaylistMapper;
import com.codeit.mpl.domain.curating.repository.PlaylistContentRepository;
import com.codeit.mpl.domain.curating.repository.PlaylistRepository;
import com.codeit.mpl.domain.curating.repository.PlaylistSubscriptionRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final PlaylistSubscriptionRepository playlistSubscriptionRepository;
  private final ContentRepository contentRepository;
  private final UserRepository userRepository;
  private final PlaylistMapper playlistMapper;

  // 플레이리스트 목록 조회
  @Transactional(readOnly = true)
  public CursorPageResponseDto<PlaylistDto> getPlaylists(
      String keywordLike,
      UUID ownerIdEqual,
      UUID subscriberIdEqual,
      int limit,
      String sortBy,
      Direction sortDirection
  ) {
    Sort.Direction direction = sortDirection == Direction.ASCENDING
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    Pageable pageable = PageRequest.of(0, limit, Sort.by(direction, sortBy));
    Page<Playlist> playlistPage = playlistRepository.findAllWithFilters(
        keywordLike, ownerIdEqual, subscriberIdEqual, pageable
    );

    List<PlaylistDto> playlistDtos = playlistPage.getContent().stream()
        .map(playlistMapper::toDto)
        .toList();

    return new CursorPageResponseDto<>(
        playlistDtos,
        null,
        null,
        playlistPage.hasNext(),
        playlistPage.getTotalElements(),
        sortBy,
        sortDirection
    );
  }

  // 플레이리스트 생성
  public PlaylistDto createPlaylist(UUID ownerId, PlaylistCreateRequest request) {
    User owner = userRepository.findById(ownerId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

    Playlist playlist = new Playlist(owner, request.title(), request.description());
    playlistRepository.save(playlist);
    return playlistMapper.toDto(playlist);
  }

  // 플레이리스트 단건 조회
  @Transactional(readOnly = true)
  public PlaylistDto getPlaylist(UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));
    return playlistMapper.toDto(playlist);
  }

  // 플레이리스트 수정
  public PlaylistDto updatePlaylist(UUID ownerId, UUID playlistId, PlaylistUpdateRequest request) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new IllegalArgumentException("플레이리스트 소유자만 수정할 수 있습니다.");
    }

    playlist.update(request.title(), request.description());
    return playlistMapper.toDto(playlist);
  }

  // 플레이리스트 삭제
  public void deletePlaylist(UUID ownerId, UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new IllegalArgumentException("플레이리스트 소유자만 삭제할 수 있습니다.");
    }

    playlistRepository.delete(playlist);
  }

  // 콘텐츠 추가
  public void addContent(UUID ownerId, UUID playlistId, UUID contentId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new IllegalArgumentException("플레이리스트 소유자만 콘텐츠를 추가할 수 있습니다.");
    }

    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    if (playlistContentRepository.existsByPlaylistAndContent(playlist, content)) {
      throw new IllegalArgumentException("이미 추가된 콘텐츠입니다.");
    }

    playlistContentRepository.save(new PlaylistContent(playlist, content));
  }

  // 콘텐츠 삭제
  public void removeContent(UUID ownerId, UUID playlistId, UUID contentId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new IllegalArgumentException("플레이리스트 소유자만 콘텐츠를 삭제할 수 있습니다.");
    }

    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 콘텐츠입니다."));

    playlistContentRepository.deleteByPlaylistAndContent(playlist, content);
  }

  // 플레이리스트 구독
  public void subscribePlaylist(UUID subscriberId, UUID playlistId) {
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));

    if (playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)) {
      throw new IllegalArgumentException("이미 구독중인 플레이리스트입니다.");
    }

    playlistSubscriptionRepository.save(new PlaylistSubscription(playlist, subscriber));
  }

  // 플레이리스트 구독 취소
  public void unsubscribePlaylist(UUID subscriberId, UUID playlistId) {
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 플레이리스트입니다."));

    playlistSubscriptionRepository.deleteByPlaylistAndSubscriber(playlist, subscriber);
  }
}