package com.codeit.mpl.domain.conversation.controller;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.service.ConversationService;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.SearchRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
  public ResponseEntity<CursorPageResponseDto<DirectMessageDto>> getConversations(
      @RequestParam("keywordLike") String keywordLike,
      @ModelAttribute SearchRequest request
  ) {
    return ResponseEntity.ok(null);
  }

  @PostMapping
  public ResponseEntity<ConversationDto> createConversation(
      @RequestBody @Valid ConversationCreateRequest request
  ) {
    conversationService.createConversation(request);
    return ResponseEntity.ok(null);
  }

  @PostMapping("{conversationId}/direct-messages/{directMessageId}/read")
  public ResponseEntity<Void> readConversationMessage(
      @PathVariable UUID conversationId,
      @PathVariable UUID directMessageId
  ) {
    return ResponseEntity.ok(null);
  }

  @GetMapping("{conversationId}")
  public ResponseEntity<ConversationDto> getConversation(
      @PathVariable UUID conversationId
  ) {
    return ResponseEntity.ok(null);
  }

  @GetMapping("{conversationId}/direct-messages")
  public ResponseEntity<ConversationDto> getDirectMessage(
      @PathVariable UUID conversationId,
      @ModelAttribute CursorPageRequestDto request
  ) {
    return ResponseEntity.ok(null);
  }

  @GetMapping("with")
  public ResponseEntity<ConversationDto> getWith(
      @RequestParam("userId") UUID userId
  ) {
    return ResponseEntity.ok(null);
  }
}
