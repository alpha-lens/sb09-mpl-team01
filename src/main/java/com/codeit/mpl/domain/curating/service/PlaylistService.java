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
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.exception.ErrorCode;
import com.codeit.mpl.infra.exception.MplException;
import com.codeit.mpl.infra.exception.playlist.InvalidPlaylistCursorException;
import com.codeit.mpl.infra.exception.playlist.InvalidPlaylistLimitException;
import com.codeit.mpl.infra.exception.playlist.InvalidPlaylistSortException;
import com.codeit.mpl.infra.exception.playlist.PlaylistContentAlreadyExistsException;
import com.codeit.mpl.infra.exception.playlist.PlaylistContentNotFoundException;
import com.codeit.mpl.infra.exception.playlist.PlaylistForbiddenException;
import com.codeit.mpl.infra.exception.playlist.PlaylistNotFoundException;
import com.codeit.mpl.infra.exception.playlist.PlaylistSubscriptionAlreadyExistsException;
import com.codeit.mpl.infra.exception.playlist.PlaylistSubscriptionNotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
  private final ApplicationEventPublisher eventPublisher;

  @Transactional(readOnly = true)
  public CursorPageResponseDto<PlaylistDto> getPlaylists(
      String keywordLike,
      UUID ownerIdEqual,
      UUID subscriberIdEqual,
      String cursor,
      UUID idAfter,
      int limit,
      String sortBy,
      Direction sortDirection
  ) {
    if (limit <= 0) {
      throw new InvalidPlaylistLimitException();
    }

    List<String> allowedSortFields = List.of("createdAt", "updatedAt", "title");
    if (!allowedSortFields.contains(sortBy)) {
      throw new InvalidPlaylistSortException();
    }

    validateCursorPair(cursor, idAfter);

    Sort.Direction direction = sortDirection == Direction.ASCENDING
        ? Sort.Direction.ASC
        : Sort.Direction.DESC;

    Pageable pageable = PageRequest.of(
        0,
        limit + 1,
        Sort.by(direction, sortBy).and(Sort.by(direction, "id"))
    );

    Specification<Playlist> filterSpec = createFilterSpecification(keywordLike, ownerIdEqual, subscriberIdEqual);
    Specification<Playlist> cursorSpec = createCursorSpecification(cursor, idAfter, sortBy, sortDirection);
    Specification<Playlist> specification = filterSpec.and(cursorSpec);

    Page<Playlist> playlistPage = playlistRepository.findAll(specification, pageable);

    List<Playlist> playlists = playlistPage.getContent();
    boolean hasNext = playlists.size() > limit;

    List<Playlist> pagePlaylists = hasNext ? playlists.subList(0, limit) : playlists;

    List<PlaylistDto> playlistDtos = pagePlaylists.stream()
        .map(playlistMapper::toDto)
        .toList();

    String nextCursor = null;
    String nextIdAfter = null;

    if (hasNext && !pagePlaylists.isEmpty()) {
      Playlist last = pagePlaylists.get(pagePlaylists.size() - 1);
      nextCursor = getCursorValue(last, sortBy);
      nextIdAfter = last.getId().toString();
    }

    long totalCount = playlistRepository.count(filterSpec);

    return new CursorPageResponseDto<>(
        playlistDtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        sortDirection
    );
  }

  public PlaylistDto createPlaylist(UUID ownerId, PlaylistCreateRequest request) {
    User owner = userRepository.findById(ownerId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));
    Playlist playlist = new Playlist(owner, request.title(), request.description());
    playlistRepository.save(playlist);
    return playlistMapper.toDto(playlist);
  }

  @Transactional(readOnly = true)
  public PlaylistDto getPlaylist(UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);
    return playlistMapper.toDto(playlist);
  }

  public PlaylistDto updatePlaylist(UUID ownerId, UUID playlistId, PlaylistUpdateRequest request) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new PlaylistForbiddenException();
    }

    playlist.update(request.title(), request.description());
    return playlistMapper.toDto(playlist);
  }

  public void deletePlaylist(UUID ownerId, UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new PlaylistForbiddenException();
    }

    playlistRepository.delete(playlist);
  }

  public void addContent(UUID ownerId, UUID playlistId, UUID contentId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new PlaylistForbiddenException();
    }

    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new MplException(ErrorCode.CONTENT_NOT_FOUND));

    if (playlistContentRepository.existsByPlaylistAndContent(playlist, content)) {
      throw new PlaylistContentAlreadyExistsException();
    }

    playlistContentRepository.save(new PlaylistContent(playlist, content));
  }

  public void removeContent(UUID ownerId, UUID playlistId, UUID contentId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new PlaylistForbiddenException();
    }

    Content content = contentRepository.findById(contentId)
        .orElseThrow(() -> new MplException(ErrorCode.CONTENT_NOT_FOUND));

    if (!playlistContentRepository.existsByPlaylistAndContent(playlist, content)) {
      throw new PlaylistContentNotFoundException();
    }

    playlistContentRepository.deleteByPlaylistAndContent(playlist, content);
  }

  public void subscribePlaylist(UUID subscriberId, UUID playlistId) {
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)) {
      throw new PlaylistSubscriptionAlreadyExistsException();
    }

    playlistSubscriptionRepository.save(new PlaylistSubscription(playlist, subscriber));

    // 소유자와 구독자가 다를 때만 알림 발행 (트랜잭션 커밋 후 AFTER_COMMIT 리스너가 처리)
    User owner = playlist.getOwner();
    if (!owner.getId().equals(subscriberId)) {
      eventPublisher.publishEvent(new NotificationEvent(
          owner,
          subscriber,
          NotificationLevel.INFO,
          "새로운 구독자",
          subscriber.getName() + "님이 '" + playlist.getTitle() + "' 플레이리스트를 구독했습니다."
      ));
    }
  }

  public void unsubscribePlaylist(UUID subscriberId, UUID playlistId) {
    User subscriber = userRepository.findById(subscriberId)
        .orElseThrow(() -> new MplException(ErrorCode.USER_NOT_FOUND));

    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, subscriber)) {
      throw new PlaylistSubscriptionNotFoundException();
    }

    playlistSubscriptionRepository.deleteByPlaylistAndSubscriber(playlist, subscriber);
  }

  private void validateCursorPair(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidPlaylistCursorException();
    }
  }

  private Specification<Playlist> createFilterSpecification(
      String keywordLike,
      UUID ownerIdEqual,
      UUID subscriberIdEqual
  ) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new java.util.ArrayList<>();

      if (keywordLike != null && !keywordLike.isBlank()) {
        predicates.add(cb.like(root.get("title"), "%" + keywordLike + "%"));
      }

      if (ownerIdEqual != null) {
        predicates.add(cb.equal(root.get("owner").get("id"), ownerIdEqual));
      }

      if (subscriberIdEqual != null) {
        var subquery = query.subquery(UUID.class);
        var subRoot = subquery.from(PlaylistSubscription.class);
        subquery.select(subRoot.get("playlist").get("id"))
            .where(cb.equal(subRoot.get("subscriber").get("id"), subscriberIdEqual));
        predicates.add(root.get("id").in(subquery));
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  private Specification<Playlist> createCursorSpecification(
      String cursor,
      UUID idAfter,
      String sortBy,
      Direction sortDirection
  ) {
    return (root, query, cb) -> {
      if (cursor == null || cursor.isBlank() || idAfter == null) {
        return cb.conjunction();
      }

      if ("createdAt".equals(sortBy) || "updatedAt".equals(sortBy)) {
        Instant cursorValue = parseInstantCursor(cursor);

        Predicate sortPredicate;
        Predicate sameSortValuePredicate;

        if (sortDirection == Direction.ASCENDING) {
          sortPredicate = cb.greaterThan(root.get(sortBy), cursorValue);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get(sortBy), cursorValue),
              cb.greaterThan(root.get("id"), idAfter)
          );
        } else {
          sortPredicate = cb.lessThan(root.get(sortBy), cursorValue);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get(sortBy), cursorValue),
              cb.lessThan(root.get("id"), idAfter)
          );
        }

        return cb.or(sortPredicate, sameSortValuePredicate);
      }

      if ("title".equals(sortBy)) {
        Predicate sortPredicate;
        Predicate sameSortValuePredicate;

        if (sortDirection == Direction.ASCENDING) {
          sortPredicate = cb.greaterThan(root.get("title"), cursor);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get("title"), cursor),
              cb.greaterThan(root.get("id"), idAfter)
          );
        } else {
          sortPredicate = cb.lessThan(root.get("title"), cursor);
          sameSortValuePredicate = cb.and(
              cb.equal(root.get("title"), cursor),
              cb.lessThan(root.get("id"), idAfter)
          );
        }

        return cb.or(sortPredicate, sameSortValuePredicate);
      }

      return cb.conjunction();
    };
  }

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new InvalidPlaylistCursorException();
    }
  }

  private String getCursorValue(Playlist playlist, String sortBy) {
    if ("createdAt".equals(sortBy)) {
      return playlist.getCreatedAt().toString();
    }
    if ("updatedAt".equals(sortBy)) {
      return playlist.getUpdatedAt().toString();
    }
    if ("title".equals(sortBy)) {
      return playlist.getTitle();
    }
    throw new InvalidPlaylistSortException();
  }
}