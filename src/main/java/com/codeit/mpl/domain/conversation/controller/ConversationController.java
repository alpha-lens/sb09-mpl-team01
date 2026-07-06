package com.codeit.mpl.domain.conversation.controller;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.service.ConversationService;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.exception.user.UserNotFoundException;
import com.codeit.mpl.infra.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
public class ConversationController {
  private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<CursorPageResponseDto<ConversationDto>> getConversations(
        @AuthenticationPrincipal UserPrincipal userPrincipal,
        @RequestParam(value = "keywordLike", required = false) String keywordLike,
        @ModelAttribute CursorPageRequestDto request
    ) {
        UUID userId = getUserId(userPrincipal);
        CursorPageResponseDto<ConversationDto> response = conversationService.getConversations(userId, keywordLike, request);
        return ResponseEntity.ok(response);
    }

  @PostMapping
  public ResponseEntity<ConversationDto> createConversation(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestBody @Valid ConversationCreateRequest request
  ) {
    UUID userId = getUserId(userPrincipal);
    ConversationDto conversationDto = conversationService.createConversation(userId, request);
    return ResponseEntity.ok(conversationDto);
  }

  @PostMapping("{conversationId}/direct-messages/{directMessageId}/read")
  public ResponseEntity<Void> readConversationMessage(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId
  ) {
    UUID userId = getUserId(userPrincipal);
    conversationService.readConversationMessages(conversationId, directMessageId, userId);
    return ResponseEntity.ok().build();
  }

  @GetMapping("{conversationId}")
  public ResponseEntity<ConversationDto> getConversation(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID conversationId
  ) {
    UUID userId = getUserId(userPrincipal);
    ConversationDto response = conversationService.getConversationDto(conversationId, userId);
    return ResponseEntity.ok(response);
  }

  @GetMapping("{conversationId}/direct-messages")
  public ResponseEntity<CursorPageResponseDto<DirectMessageDto>> getDirectMessage(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @PathVariable UUID conversationId,
      @ModelAttribute CursorPageRequestDto request
  ) {
    UUID userId = getUserId(userPrincipal);
    CursorPageResponseDto<DirectMessageDto> response = conversationService.getDirectMessages(conversationId, userId, request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("with")
  public ResponseEntity<ConversationDto> getWith(
      @AuthenticationPrincipal UserPrincipal userPrincipal,
      @RequestParam("userId") UUID targetUserId
  ) {
    UUID userId = getUserId(userPrincipal);
    ConversationDto response = conversationService.getWith(userId, targetUserId);
    if (response == null) {
        return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(response);
  }

  private UUID getUserId(UserPrincipal userPrincipal) {
    if (userPrincipal == null) {
      throw new IllegalArgumentException("인증 정보가 유효하지 않습니다.");
    }
    UUID userId = userPrincipal.userId();
    if (userId == null) {
      throw new UserNotFoundException();
    }
    return userId;
  }
}
