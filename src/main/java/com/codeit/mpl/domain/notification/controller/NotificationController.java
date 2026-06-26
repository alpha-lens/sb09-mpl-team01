package com.codeit.mpl.domain.notification.controller;

import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.service.NotificationService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.sse.SseService;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Validated
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService notificationService;
  private final UserRepository userRepository;
  private final SseService sseService;

  /**
   * Retrieves a page of notifications for the authenticated user using cursor-based pagination.
   *
   * @param cursor         the cursor position for pagination (optional)
   * @param idAfter        the notification ID after which to fetch records (optional)
   * @param limit          the maximum number of notifications to return
   * @param sortDirection  the sort direction for results
   * @param sortBy         the field to sort by
   * @return               a paginated response containing notifications
   */
  @GetMapping
  public ResponseEntity<CursorPageResponseDto<NotificationDto>> getNotifications(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Instant idAfter,
      @RequestParam @Min(1) int limit,
      @RequestParam Direction sortDirection,
      @RequestParam String sortBy
  ) {
    // 임시 회원 바인딩 로직: 인증 유저 정보가 없을 시 에러 혹은 기본값 제공
    UUID userId = getUserIdFromUserDetails(userDetails);

    log.debug("[알림] 목록 조회 요청. userId={}, limit={}, cursor존재={}, after존재={}",
        userId, limit, cursor != null && !cursor.isBlank(), idAfter != null
    );

    CursorPageResponseDto<NotificationDto> response = notificationService.getNotifications(
        userId, cursor, idAfter, limit
    );

    log.debug(
        "[알림] 목록 조회 완료. userId={}, 반환개수={}, 다음페이지존재={}",
        userId, response.data().size(), response.hasNext()
    );

    return ResponseEntity.ok(response);
  }

  /**
   * Deletes the specified notification.
   *
   * @return a response with HTTP status 204 No Content
   */
  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> deleteNotification(
      @PathVariable UUID notificationId
  ) {
    notificationService.deleteNotification(notificationId);
    return ResponseEntity.noContent().build();
  }

  /**
   * Resolves the authenticated user's UUID from UserDetails.
   *
   * @param  userDetails the authentication details of the current user
   * @return the user's UUID
   * @throws IllegalArgumentException if userDetails is null or if no user exists with the username from userDetails
   */
  private UUID getUserIdFromUserDetails(UserDetails userDetails) {
    if (userDetails == null) {
      // 비로그인 테스트 대응 또는 예외 처리
      throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
    }
    return userRepository.findByEmail(userDetails.getUsername())
        .map(User::getId)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));
  }
}
