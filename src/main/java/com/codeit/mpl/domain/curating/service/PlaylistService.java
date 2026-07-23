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
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.profile.entity.Follow;
import com.codeit.mpl.domain.profile.repository.FollowRepository;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
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
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final PlaylistContentRepository playlistContentRepository;
  private final PlaylistSubscriptionRepository playlistSubscriptionRepository;
  private final ContentRepository contentRepository;
  private final UserRepository userRepository;
  private final ReviewRepository reviewRepository;
  private final FollowRepository followRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final BinaryContentStorage binaryContentStorage;

  @Transactional(readOnly = true)
  public CursorPageResponseDto<PlaylistDto> getPlaylists(
          String keywordLike,
          UUID ownerIdEqual,
          UUID subscriberIdEqual,
          String cursor,
          UUID idAfter,
          int limit,
          String sortBy,
          Direction sortDirection,
          UUID currentUserId
  ) {
    log.debug(
            "플레이리스트 목록 조회 요청 - keyword={}, ownerId={}, subscriberId={}, limit={}, sortBy={}, sortDirection={}",
            keywordLike,
            ownerIdEqual,
            subscriberIdEqual,
            limit,
            sortBy,
            sortDirection
    );

    if (limit <= 0) {
      log.warn("잘못된 limit 값으로 플레이리스트 목록 조회 시도 - limit={}", limit);
      throw new InvalidPlaylistLimitException();
    }

    List<String> allowedSortFields = List.of("createdAt", "updatedAt", "title");
    if (sortBy == null || !allowedSortFields.contains(sortBy)) { // null 체크 추가!
      log.debug("허용되지 않은 sortBy={} -> createdAt으로 대체", sortBy);
      sortBy = "createdAt";
    }

    validateCursorPair(cursor, idAfter);

    Sort.Direction direction =
            sortDirection == Direction.ASCENDING
                    ? Sort.Direction.ASC
                    : Sort.Direction.DESC;

    Pageable pageable = PageRequest.of(
            0,
            limit + 1,
            Sort.by(direction, sortBy)
                    .and(Sort.by(direction, "id"))
    );

    Specification<Playlist> filterSpec =
            createFilterSpecification(
                    keywordLike,
                    ownerIdEqual,
                    subscriberIdEqual
            );

    Specification<Playlist> cursorSpec =
            createCursorSpecification(
                    cursor,
                    idAfter,
                    sortBy,
                    sortDirection
            );

    Specification<Playlist> specification =
            filterSpec.and(cursorSpec);

    Page<Playlist> playlistPage =
            playlistRepository.findAll(
                    specification,
                    pageable
            );

    List<Playlist> playlists =
            playlistPage.getContent();

    boolean hasNext =
            playlists.size() > limit;

    List<Playlist> pagePlaylists =
            hasNext
                    ? playlists.subList(0, limit)
                    : playlists;

    List<UUID> playlistIds =
            pagePlaylists.stream()
                    .map(Playlist::getId)
                    .toList();
    if (playlistIds.isEmpty()) {
      long totalCount = playlistRepository.count(filterSpec);
      return new CursorPageResponseDto<>(
          Collections.emptyList(), null, null, false, totalCount, sortBy, sortDirection
      );
    }

    Map<UUID, Long> subscriberCountMap =
            playlistSubscriptionRepository
                    .findSubscriptionStats(playlistIds)
                    .stream()
                    .collect(
                            Collectors.toMap(
                                    row -> (UUID) row[0],
                                    row -> (Long) row[1]
                            )
                    );

    Set<UUID> subscribedPlaylistIds =
            new HashSet<>();

    if (currentUserId != null) {
      User currentUser =
              userRepository
                      .findById(currentUserId)
                      .orElse(null);

      if (currentUser != null) {
        subscribedPlaylistIds =
                new HashSet<>(
                        playlistSubscriptionRepository
                                .findSubscribedPlaylistIds(
                                        playlistIds,
                                        currentUser
                                )
                );
      }
    }

    final Set<UUID> finalSubscribedIds =
        subscribedPlaylistIds;

    // --- 최적화: 콘텐츠를 한 번에 다 가져와서 맵으로 그룹화 ---
    List<PlaylistContent> allContents = playlistContentRepository.findByPlaylistIdIn(playlistIds);
    Map<UUID, List<PlaylistContent>> contentMap = allContents.stream()
        .collect(Collectors.groupingBy(pc -> pc.getPlaylist().getId()));

    List<PlaylistDto> playlistDtos =
        pagePlaylists.stream()
            .map(
                playlist ->
                    toDtoSimple(
                        playlist,
                        subscriberCountMap.getOrDefault(
                            playlist.getId(),
                            0L
                        ),
                        finalSubscribedIds.contains(
                            playlist.getId()
                        ),
                        contentMap.getOrDefault(playlist.getId(), new ArrayList<>())
                    )
            )
            .toList();

    String nextCursor = null;
    String nextIdAfter = null;

    if (hasNext && !pagePlaylists.isEmpty()) {
      Playlist last =
              pagePlaylists.get(
                      pagePlaylists.size() - 1
              );

      nextCursor =
              getCursorValue(
                      last,
                      sortBy
              );

      nextIdAfter =
              last.getId().toString();
    }

    long totalCount =
            playlistRepository.count(filterSpec);

    log.debug(
            "플레이리스트 목록 조회 완료 - 결과 {}건, totalCount={}",
            playlistDtos.size(),
            totalCount
    );

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

  public PlaylistDto createPlaylist(
          UUID ownerId,
          PlaylistCreateRequest request
  ) {
    log.info(
            "플레이리스트 생성 요청 - ownerId={}, title={}",
            ownerId,
            request.title()
    );

    User owner =
            userRepository
                    .findById(ownerId)
                    .orElseThrow(
                            () ->
                                    new MplException(
                                            ErrorCode.USER_NOT_FOUND
                                    )
                    );

    Playlist playlist =
            new Playlist(
                    owner,
                    request.title(),
                    request.description()
            );

    playlistRepository.save(playlist);

    log.info(
            "플레이리스트 생성 완료 - playlistId={}, ownerId={}",
            playlist.getId(),
            ownerId
    );

    List<Follow> followers =
            followRepository.findByFollowee(owner);

    log.debug(
            "신규 플레이리스트 알림 대상 {}명 - ownerId={}",
            followers.size(),
            ownerId
    );

    for (Follow follow : followers) {
      eventPublisher.publishEvent(
              new NotificationEvent(
                      follow.getFollower(),
                      owner,
                      NotificationLevel.INFO,
                      "새 플레이리스트 알림",
                      owner.getName()
                              + "님이 새 플레이리스트 ["
                              + playlist.getTitle()
                              + "]를 생성했습니다.",
                      NotificationType.PLAYLIST_ADDED,
                      playlist.getId()
              )
      );
    }

    return toDto(
            playlist,
            ownerId
    );
  }

  @Transactional(readOnly = true)
  public PlaylistDto getPlaylist(
          UUID playlistId,
          UUID currentUserId
  ) {
    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            () -> {
                              log.warn(
                                      "존재하지 않는 플레이리스트 조회 시도 - playlistId={}",
                                      playlistId
                              );

                              return new PlaylistNotFoundException();
                            }
                    );

    return toDto(
            playlist,
            currentUserId
    );
  }

  public PlaylistDto updatePlaylist(
          UUID ownerId,
          UUID playlistId,
          PlaylistUpdateRequest request
  ) {
    log.info(
            "플레이리스트 수정 요청 - playlistId={}, ownerId={}",
            playlistId,
            ownerId
    );

    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            PlaylistNotFoundException::new
                    );

    if (!playlist.getOwner()
            .getId()
            .equals(ownerId)) {

      log.warn(
              "플레이리스트 수정 권한 없음 - playlistId={}, requesterId={}, ownerId={}",
              playlistId,
              ownerId,
              playlist.getOwner().getId()
      );

      throw new PlaylistForbiddenException();
    }

    playlist.update(
            request.title(),
            request.description()
    );

    log.info(
            "플레이리스트 수정 완료 - playlistId={}",
            playlistId
    );

    return toDto(
            playlist,
            ownerId
    );
  }

  public void deletePlaylist(
          UUID ownerId,
          UUID playlistId
  ) {
    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            PlaylistNotFoundException::new
                    );

    if (!playlist.getOwner()
            .getId()
            .equals(ownerId)) {

      log.warn(
              "플레이리스트 삭제 권한 없음 - playlistId={}, requesterId={}, ownerId={}",
              playlistId,
              ownerId,
              playlist.getOwner().getId()
      );

      throw new PlaylistForbiddenException();
    }

    playlistContentRepository
            .deleteByPlaylist(playlist);

    playlistSubscriptionRepository
            .deleteByPlaylist(playlist);

    playlistRepository.delete(playlist);

    log.info(
            "플레이리스트 삭제 완료 - playlistId={}, ownerId={}",
            playlistId,
            ownerId
    );
  }

  public void addContent(
          UUID ownerId,
          UUID playlistId,
          UUID contentId
  ) {
    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            PlaylistNotFoundException::new
                    );

    if (!playlist.getOwner()
            .getId()
            .equals(ownerId)) {

      log.warn(
              "콘텐츠 추가 권한 없음 - playlistId={}, requesterId={}",
              playlistId,
              ownerId
      );

      throw new PlaylistForbiddenException();
    }

    Content content =
            contentRepository
                    .findById(contentId)
                    .orElseThrow(
                            () ->
                                    new MplException(
                                            ErrorCode.CONTENT_NOT_FOUND
                                    )
                    );

    if (playlistContentRepository
            .existsByPlaylistAndContent(
                    playlist,
                    content
            )) {

      log.warn(
              "이미 존재하는 콘텐츠 추가 시도 - playlistId={}, contentId={}",
              playlistId,
              contentId
      );

      throw new PlaylistContentAlreadyExistsException();
    }

    playlistContentRepository.save(
            new PlaylistContent(
                    playlist,
                    content
            )
    );

    log.info(
            "플레이리스트에 콘텐츠 추가 완료 - playlistId={}, contentId={}",
            playlistId,
            contentId
    );

    List<PlaylistSubscription> subscriptions =
            playlistSubscriptionRepository
                    .findByPlaylist(playlist);

    log.debug(
            "콘텐츠 추가 알림 대상 {}명 - playlistId={}",
            subscriptions.size(),
            playlistId
    );

    for (PlaylistSubscription subscription : subscriptions) {
      User subscriber =
              subscription.getSubscriber();

      if (!subscriber.getId()
              .equals(
                      playlist.getOwner().getId()
              )) {

        eventPublisher.publishEvent(
                new NotificationEvent(
                        subscriber,
                        playlist.getOwner(),
                        NotificationLevel.INFO,
                        "플레이리스트 컨텐츠 추가 알림",
                        "플레이리스트 ["
                                + playlist.getTitle()
                                + "]에 새 컨텐츠 ["
                                + content.getTitle()
                                + "]가 추가되었습니다.",
                        NotificationType.PLAYLIST_CONTENT_ADDED,
                        playlist.getId()
                )
        );
      }
    }
  }

  public void removeContent(
          UUID ownerId,
          UUID playlistId,
          UUID contentId
  ) {
    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            PlaylistNotFoundException::new
                    );

    if (!playlist.getOwner()
            .getId()
            .equals(ownerId)) {

      log.warn(
              "콘텐츠 삭제 권한 없음 - playlistId={}, requesterId={}",
              playlistId,
              ownerId
      );

      throw new PlaylistForbiddenException();
    }

    Content content =
            contentRepository
                    .findById(contentId)
                    .orElseThrow(
                            () ->
                                    new MplException(
                                            ErrorCode.CONTENT_NOT_FOUND
                                    )
                    );

    if (!playlistContentRepository
            .existsByPlaylistAndContent(
                    playlist,
                    content
            )) {

      log.warn(
              "존재하지 않는 콘텐츠 삭제 시도 - playlistId={}, contentId={}",
              playlistId,
              contentId
      );

      throw new PlaylistContentNotFoundException();
    }

    playlistContentRepository
            .deleteByPlaylistAndContent(
                    playlist,
                    content
            );

    log.info(
            "플레이리스트에서 콘텐츠 삭제 완료 - playlistId={}, contentId={}",
            playlistId,
            contentId
    );

    List<PlaylistSubscription> subscriptions =
            playlistSubscriptionRepository
                    .findByPlaylist(playlist);

    for (PlaylistSubscription subscription : subscriptions) {
      User subscriber =
              subscription.getSubscriber();

      if (!subscriber.getId()
              .equals(
                      playlist.getOwner().getId()
              )) {

        eventPublisher.publishEvent(
                new NotificationEvent(
                        subscriber,
                        playlist.getOwner(),
                        NotificationLevel.INFO,
                        "플레이리스트 컨텐츠 삭제 알림",
                        "플레이리스트 ["
                                + playlist.getTitle()
                                + "]에서 컨텐츠 ["
                                + content.getTitle()
                                + "]가 삭제되었습니다.",
                        NotificationType.PLAYLIST_CONTENT_REMOVED,
                        playlist.getId()
                )
        );
      }
    }
  }

  public void subscribePlaylist(
          UUID subscriberId,
          UUID playlistId
  ) {
    User subscriber =
            userRepository
                    .findById(subscriberId)
                    .orElseThrow(
                            () ->
                                    new MplException(
                                            ErrorCode.USER_NOT_FOUND
                                    )
                    );

    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            PlaylistNotFoundException::new
                    );

    if (playlistSubscriptionRepository
            .existsByPlaylistAndSubscriber(
                    playlist,
                    subscriber
            )) {

      log.warn(
              "이미 구독 중인 플레이리스트 재구독 시도 - playlistId={}, subscriberId={}",
              playlistId,
              subscriberId
      );

      throw new PlaylistSubscriptionAlreadyExistsException();
    }

    playlistSubscriptionRepository.save(
            new PlaylistSubscription(
                    playlist,
                    subscriber
            )
    );

    log.info(
            "플레이리스트 구독 완료 - playlistId={}, subscriberId={}",
            playlistId,
            subscriberId
    );

    if (!playlist.getOwner()
            .getId()
            .equals(subscriber.getId())) {

      eventPublisher.publishEvent(
              new NotificationEvent(
                      playlist.getOwner(),
                      subscriber,
                      NotificationLevel.INFO,
                      "플레이리스트 구독 알림",
                      subscriber.getName()
                              + "님이 회원님의 플레이리스트 ["
                              + playlist.getTitle()
                              + "]를 구독하기 시작했습니다.",
                      NotificationType.PLAYLIST_SUBSCRIBED,
                      playlist.getId()
              )
      );
    }
  }

  public void unsubscribePlaylist(
          UUID subscriberId,
          UUID playlistId
  ) {
    User subscriber =
            userRepository
                    .findById(subscriberId)
                    .orElseThrow(
                            () ->
                                    new MplException(
                                            ErrorCode.USER_NOT_FOUND
                                    )
                    );

    Playlist playlist =
            playlistRepository
                    .findById(playlistId)
                    .orElseThrow(
                            PlaylistNotFoundException::new
                    );

    if (!playlistSubscriptionRepository
            .existsByPlaylistAndSubscriber(
                    playlist,
                    subscriber
            )) {

      log.warn(
              "구독하지 않은 플레이리스트 구독취소 시도 - playlistId={}, subscriberId={}",
              playlistId,
              subscriberId
      );

      throw new PlaylistSubscriptionNotFoundException();
    }

    playlistSubscriptionRepository
            .deleteByPlaylistAndSubscriber(
                    playlist,
                    subscriber
            );

    log.info(
            "플레이리스트 구독취소 완료 - playlistId={}, subscriberId={}",
            playlistId,
            subscriberId
    );
  }

  // User.profileImageUrl 컬럼엔 S3 key가 저장되므로, 응답 시점마다 presigned URL로 변환해서 내려준다.
  private String resolveProfileImageUrl(User owner) {
    return owner.getProfileImageUrl() != null
        ? binaryContentStorage.getUrl(owner.getProfileImageUrl())
        : null;
  }

  private String resolveThumbnailUrl(String rawUrl) {
    if (rawUrl == null || rawUrl.isBlank()) {
      return null;
    }
    if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://") || rawUrl.startsWith("/uploads/")) {
      return rawUrl;
    }
    return binaryContentStorage.getUrl(rawUrl);
  }

  private PlaylistDto toDto(
          Playlist playlist,
          UUID currentUserId
  ) {
    UserSummary owner =
            new UserSummary(
                    playlist.getOwner().getId(),
                    playlist.getOwner().getName(),
                    resolveProfileImageUrl(playlist.getOwner())
            );

    long subscriberCount =
            playlistSubscriptionRepository
                    .countByPlaylist(playlist);

    boolean subscribedByMe = false;

    if (currentUserId != null) {
      User currentUser =
              userRepository
                      .findById(currentUserId)
                      .orElse(null);

      if (currentUser != null) {
        subscribedByMe =
                playlistSubscriptionRepository
                        .existsByPlaylistAndSubscriber(
                                playlist,
                                currentUser
                        );
      }
    }

    List<ContentSummary> contents =
            playlistContentRepository
                    .findByPlaylist(playlist)
                    .stream()
                    .map(
                            playlistContent -> {
                              Content content =
                                      playlistContent.getContent();

                              Double averageRating =
                                      reviewRepository
                                              .findAverageRatingByContent(
                                                      content
                                              );

                              long reviewCount =
                                      reviewRepository
                                              .countByContent(content);

                              return new ContentSummary(
                                      content.getId(),
                                      content.getType(),
                                      content.getTitle(),
                                      content.getDescription(),
                                      resolveThumbnailUrl(content.getThumbnailUrl()),
                                      content.getTags(),
                                      averageRating != null
                                              ? averageRating
                                              : 0.0,
                                      (int) reviewCount,
                                      content.getWatcherCount()
                              );
                            }
                    )
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

  private PlaylistDto toDtoSimple(
      Playlist playlist,
      long subscriberCount,
      boolean subscribedByMe,
      List<PlaylistContent> contents
  ) {
    UserSummary owner =
        new UserSummary(
            playlist.getOwner().getId(),
            playlist.getOwner().getName(),
            resolveProfileImageUrl(playlist.getOwner())
        );

    List<ContentSummary> contentSummaries =
        new ArrayList<>();

    contents.forEach(
        playlistContent -> {
          Content content =
              playlistContent.getContent();

          contentSummaries.add(
              new ContentSummary(
                  content.getId(),
                  content.getType(),
                  content.getTitle(),
                  content.getDescription(),
                  resolveThumbnailUrl(content.getThumbnailUrl()),
                  content.getTags(),
                  0.0,
                  0,
                  content.getWatcherCount()
              )
          );
        }
    );

    return new PlaylistDto(
        playlist.getId(),
        owner,
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        subscriberCount,
        subscribedByMe,
        contentSummaries
    );
  }

  private void validateCursorPair(
          String cursor,
          UUID idAfter
  ) {
    boolean hasCursor =
            cursor != null
                    && !cursor.isBlank();

    boolean hasIdAfter =
            idAfter != null;

    if (hasCursor != hasIdAfter) {
      throw new InvalidPlaylistCursorException();
    }
  }

  private Specification<Playlist> createFilterSpecification(
          String keywordLike,
          UUID ownerIdEqual,
          UUID subscriberIdEqual
  ) {
    return (root, query, criteriaBuilder) -> {
      List<Predicate> predicates =
              new ArrayList<>();

      if (keywordLike != null
              && !keywordLike.isBlank()) {

        predicates.add(
                criteriaBuilder.like(
                        root.get("title"),
                        "%" + keywordLike + "%"
                )
        );
      }

      if (ownerIdEqual != null) {
        predicates.add(
                criteriaBuilder.equal(
                        root.get("owner").get("id"),
                        ownerIdEqual
                )
        );
      }

      if (subscriberIdEqual != null) {
        var subquery =
                query.subquery(UUID.class);

        var subRoot =
                subquery.from(
                        PlaylistSubscription.class
                );

        subquery
                .select(
                        subRoot.get("playlist")
                                .get("id")
                )
                .where(
                        criteriaBuilder.equal(
                                subRoot.get("subscriber")
                                        .get("id"),
                                subscriberIdEqual
                        )
                );

        predicates.add(
                root.get("id")
                        .in(subquery)
        );
      }

      return criteriaBuilder.and(
              predicates.toArray(
                      new Predicate[0]
              )
      );
    };
  }

  private Specification<Playlist> createCursorSpecification(
          String cursor,
          UUID idAfter,
          String sortBy,
          Direction sortDirection
  ) {
    return (root, query, criteriaBuilder) -> {
      if (cursor == null
              || cursor.isBlank()
              || idAfter == null) {

        return criteriaBuilder.conjunction();
      }

      if ("createdAt".equals(sortBy)
              || "updatedAt".equals(sortBy)) {

        Instant cursorValue =
                parseInstantCursor(cursor);

        Predicate sortPredicate;
        Predicate sameSortValuePredicate;

        if (sortDirection == Direction.ASCENDING) {
          sortPredicate =
                  criteriaBuilder.greaterThan(
                          root.get(sortBy),
                          cursorValue
                  );

          sameSortValuePredicate =
                  criteriaBuilder.and(
                          criteriaBuilder.equal(
                                  root.get(sortBy),
                                  cursorValue
                          ),
                          criteriaBuilder.greaterThan(
                                  root.get("id"),
                                  idAfter
                          )
                  );
        } else {
          sortPredicate =
                  criteriaBuilder.lessThan(
                          root.get(sortBy),
                          cursorValue
                  );

          sameSortValuePredicate =
                  criteriaBuilder.and(
                          criteriaBuilder.equal(
                                  root.get(sortBy),
                                  cursorValue
                          ),
                          criteriaBuilder.lessThan(
                                  root.get("id"),
                                  idAfter
                          )
                  );
        }

        return criteriaBuilder.or(
                sortPredicate,
                sameSortValuePredicate
        );
      }

      if ("title".equals(sortBy)) {
        Predicate sortPredicate;
        Predicate sameSortValuePredicate;

        if (sortDirection == Direction.ASCENDING) {
          sortPredicate =
                  criteriaBuilder.greaterThan(
                          root.get("title"),
                          cursor
                  );

          sameSortValuePredicate =
                  criteriaBuilder.and(
                          criteriaBuilder.equal(
                                  root.get("title"),
                                  cursor
                          ),
                          criteriaBuilder.greaterThan(
                                  root.get("id"),
                                  idAfter
                          )
                  );
        } else {
          sortPredicate =
                  criteriaBuilder.lessThan(
                          root.get("title"),
                          cursor
                  );

          sameSortValuePredicate =
                  criteriaBuilder.and(
                          criteriaBuilder.equal(
                                  root.get("title"),
                                  cursor
                          ),
                          criteriaBuilder.lessThan(
                                  root.get("id"),
                                  idAfter
                          )
                  );
        }

        return criteriaBuilder.or(
                sortPredicate,
                sameSortValuePredicate
        );
      }

      return criteriaBuilder.conjunction();
    };
  }

  private Instant parseInstantCursor(
          String cursor
  ) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException exception) {
      throw new InvalidPlaylistCursorException();
    }
  }

  private String getCursorValue(
          Playlist playlist,
          String sortBy
  ) {
    if ("createdAt".equals(sortBy)) {
      return playlist
              .getCreatedAt()
              .toString();
    }

    if ("updatedAt".equals(sortBy)) {
      return playlist
              .getUpdatedAt()
              .toString();
    }

    if ("title".equals(sortBy)) {
      return playlist.getTitle();
    }

    throw new InvalidPlaylistSortException();
  }
}