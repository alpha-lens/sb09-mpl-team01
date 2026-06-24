package com.codeit.mpl.domain.conversation.controller;

import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageSearchRequest;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
  @GetMapping
  public ResponseEntity<CursorPageResponseDto<DirectMessageDto>> getDM(
      @ModelAttribute DirectMessageSearchRequest request
  ) {
    return ResponseEntity.ok(null);
  }
}
