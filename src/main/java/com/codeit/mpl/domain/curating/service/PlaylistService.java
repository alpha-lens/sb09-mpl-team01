package com.codeit.mpl.domain.curating.service;

import com.codeit.mpl.domain.content.dto.response.ContentSummary;
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
import com.codeit.mpl.domain.user.dto.response.UserSummary;
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
      sortBy = "createdAt";
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
        .map(p -> toDtoSimple(p))
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
    return toDto(playlist, ownerId);
  }

  @Transactional(readOnly = true)
  public PlaylistDto getPlaylist(UUID playlistId, UUID currentUserId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);
    return toDto(playlist, currentUserId);
  }

  public PlaylistDto updatePlaylist(UUID ownerId, UUID playlistId, PlaylistUpdateRequest request) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new PlaylistForbiddenException();
    }

    playlist.update(request.title(), request.description());
    return toDto(playlist, ownerId);
  }

  public void deletePlaylist(UUID ownerId, UUID playlistId) {
    Playlist playlist = playlistRepository.findById(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwner().getId().equals(ownerId)) {
      throw new PlaylistForbiddenException();
    }

    // 플레이리스트 콘텐츠 먼저 삭제
    playlistContentRepository.deleteByPlaylist(playlist);
    // 플레이리스트 구독 먼저 삭제
    playlistSubscriptionRepository.deleteByPlaylist(playlist);

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

  // ===== private helper =====

  private PlaylistDto toDto(Playlist playlist, UUID currentUserId) {
    UserSummary owner = new UserSummary(
        playlist.getOwner().getId(),
        playlist.getOwner().getName(),
        playlist.getOwner().getProfileImageUrl()
    );

    long subscriberCount = playlistSubscriptionRepository.countByPlaylist(playlist);

    boolean subscribedByMe = false;
    if (currentUserId != null) {
      User currentUser = userRepository.findById(currentUserId).orElse(null);
      if (currentUser != null) {
        subscribedByMe = playlistSubscriptionRepository.existsByPlaylistAndSubscriber(playlist, currentUser);
      }
    }

    List<ContentSummary> contents = playlistContentRepository.findByPlaylist(playlist)
        .stream()
        .map(pc -> {
          Content c = pc.getContent();
          return new ContentSummary(
              c.getId(),
              c.getType(),
              c.getTitle(),
              c.getDescription(),
              c.getThumbnailUrl(),
              List.of(), // tags는 lazy라 빈 리스트로 처리
              0.0,
              0
          );
        })
        .toList();

    return new PlaylistDto(
        playlist.getId(),
        owner,
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        subscriberCount,
        subscribedByMe,
        contents
    );
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

  private PlaylistDto toDtoSimple(Playlist playlist) {
    UserSummary owner = new UserSummary(
        playlist.getOwner().getId(),
        playlist.getOwner().getName(),
        playlist.getOwner().getProfileImageUrl()
    );

    return new PlaylistDto(
        playlist.getId(),
        owner,
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        0L,
        false,
        List.of()
    );
  }
}