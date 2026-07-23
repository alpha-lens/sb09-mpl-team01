package com.codeit.mpl.domain.notification.controller;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.service.NotificationService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.common.dto.SearchRequest;
import com.codeit.mpl.infra.sse.SseService;
import com.codeit.mpl.infra.exception.user.UserNotFoundException;
import com.codeit.mpl.infra.security.UserPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/notifications")
public class NotificationController {
  private final NotificationService notificationService;
  private final UserRepository userRepository;
  private final SseService sseService;

  @GetMapping
  public ResponseEntity<CursorPageResponseDto<NotificationDto>> getNotifications(
      @AuthenticationPrincipal UserDetails userDetails,
      @ModelAttribute SearchRequest request
  ) {
    UUID userId = getUserIdFromUserDetails(userDetails);

    String cursor = request.cursor();
    UUID idAfter = request.idAfter();
    int limit = request.limit();
    Direction sortDirection = request.sortDirection();
    String sortBy =  request.sortBy();

    log.debug("[알림] 목록 조회 요청. userId={}, limit={}, cursor존재={}, after존재={}",
        userId, limit, cursor != null && !cursor.isBlank(), idAfter != null
    );

    CursorPageResponseDto<NotificationDto> response =
        notificationService.getNotifications(userId, cursor, idAfter, limit, sortDirection);

    log.debug(
        "[알림] 목록 조회 완료. userId={}, 반환개수={}, 다음페이지존재={}",
        userId, response.data().size(), response.hasNext()
    );

    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> deleteNotification(
      @PathVariable UUID notificationId
  ) {
    log.info("[알림 삭제 요청] notificationId: {}", notificationId);
    notificationService.deleteNotification(notificationId);
    log.info("[알림 삭제 완료] notificationId: {}", notificationId);
    return ResponseEntity.noContent().build();
  }

  private UUID getUserIdFromUserDetails(UserDetails userDetails) {
    if (userDetails == null) {
      throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
    }
    return (userDetails instanceof UserPrincipal)
        ? ((UserPrincipal) userDetails).userId()
        : userRepository.findByEmail(userDetails.getUsername())
            .map(User::getId)
            .orElseThrow(UserNotFoundException::new);
  }
}
