package com.codeit.mpl.domain.conversation.controller;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.service.ConversationService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.SearchRequest;
import com.codeit.mpl.infra.exception.user.UserNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {
  private final ConversationService conversationService;
  private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<CursorPageResponseDto<ConversationDto>> getConversations(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(value = "keywordLike", required = false) String keywordLike,
        @ModelAttribute CursorPageRequestDto request
    ) {
        UUID userId = getUserId(userDetails);
        log.info("[채팅방 목록 조회 요청] userId: {}, keywordLike: {}, limit: {}, cursor: {}", userId, keywordLike, request.limit(), request.cursor());
        CursorPageResponseDto<ConversationDto> response = conversationService.getConversations(userId, keywordLike, request);
        log.info("[채팅방 목록 조회 완료] userId: {}, 조회된 방 개수: {}", userId, response.data().size());
        return ResponseEntity.ok(response);
    }

  @PostMapping
  public ResponseEntity<ConversationDto> createConversation(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestBody @Valid ConversationCreateRequest request
  ) {
    UUID userId = getUserId(userDetails);
    log.info("[채팅방 생성 요청] userId: {}, targetUserId: {}", userId, request.withUserId());
    ConversationDto conversationDto = conversationService.createConversation(userId, request);
    log.info("[채팅방 생성 완료] userId: {}, conversationId: {}", userId, conversationDto.id());
    return ResponseEntity.ok(conversationDto);
  }

  @PostMapping("{conversationId}/direct-messages/{directMessageId}/read")
  public ResponseEntity<Void> readConversationMessage(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId
  ) {
    UUID userId = getUserId(userDetails);
    log.info("[메시지 읽음 처리 요청] userId: {}, conversationId: {}, messageId: {}", userId, conversationId, directMessageId);
    conversationService.readConversationMessages(conversationId, directMessageId, userId);
    log.info("[메시지 읽음 처리 완료] userId: {}, conversationId: {}, messageId: {}", userId, conversationId, directMessageId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("{conversationId}")
  public ResponseEntity<ConversationDto> getConversation(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID conversationId
  ) {
    UUID userId = getUserId(userDetails);
    log.info("[채팅방 상세 조회 요청] userId: {}, conversationId: {}", userId, conversationId);
    ConversationDto response = conversationService.getConversationDto(conversationId, userId);
    log.info("[채팅방 상세 조회 완료] userId: {}, conversationId: {}", userId, conversationId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("{conversationId}/direct-messages")
  public ResponseEntity<CursorPageResponseDto<DirectMessageDto>> getDirectMessage(
      @AuthenticationPrincipal UserDetails userDetails,
      @PathVariable UUID conversationId,
      @ModelAttribute CursorPageRequestDto request
  ) {
    UUID userId = getUserId(userDetails);
    log.info("[메시지 목록 조회 요청] userId: {}, conversationId: {}, limit: {}, cursor: {}", userId, conversationId, request.limit(), request.cursor());
    CursorPageResponseDto<DirectMessageDto> response = conversationService.getDirectMessages(conversationId, userId, request);
    log.info("[메시지 목록 조회 완료] userId: {}, conversationId: {}, 조회된 메시지 개수: {}", userId, conversationId, response.data().size());
    return ResponseEntity.ok(response);
  }

  @GetMapping("with")
  public ResponseEntity<ConversationDto> getWith(
      @AuthenticationPrincipal UserDetails userDetails,
      @RequestParam("userId") UUID targetUserId
  ) {
    UUID userId = getUserId(userDetails);
    log.info("[상대방과의 채팅방 조회 요청] userId: {}, targetUserId: {}", userId, targetUserId);
    ConversationDto response = conversationService.getWith(userId, targetUserId);
    log.info("[상대방과의 채팅방 조회 완료] userId: {}, targetUserId: {}, 존재 여부: {}", userId, targetUserId, response != null);
    if (response == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }

  private UUID getUserId(UserDetails userDetails) {
    if (userDetails == null) {
      throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
    }
    return userRepository.findByEmail(userDetails.getUsername())
        .map(User::getId)
        .orElseThrow(UserNotFoundException::new);
  }
}
