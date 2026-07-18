package com.codeit.mpl.domain.chat.controller;

import com.codeit.mpl.domain.content.dto.ContentChatDto;
import com.codeit.mpl.domain.content.dto.ContentChatSendRequest;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageSendRequest;
import com.codeit.mpl.domain.conversation.service.ConversationService;
import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.sse.SseService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebsocketController {

    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final SseService sseService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final WatchingSessionService watchingSessionService;
    private final com.codeit.mpl.infra.storage.BinaryContentStorage binaryContentStorage;

    @MessageMapping("/contents/{contentId}/chat")
    public void handleContentChat(
            @DestinationVariable UUID contentId,
            @Valid ContentChatSendRequest request,
            Principal principal
    ) {
        String email = getEmailFromPrincipal(principal);
        if (email == null) return;

        userRepository.findByEmail(email).ifPresent(user -> {
            // DB에는 S3 key가 저장되므로 presigned URL로 변환해서 내려준다.
            String resolvedImageUrl = user.getProfileImageUrl() != null
                    ? binaryContentStorage.getUrl(user.getProfileImageUrl())
                    : null;
            UserSummary sender = new UserSummary(user.getId(), user.getName(), resolvedImageUrl);
            ContentChatDto chatDto = new ContentChatDto(
                    UUID.randomUUID(),
                    contentId,
                    sender,
                    request.content(),
                    Instant.now()
            );

            messagingTemplate.convertAndSend("/sub/contents/" + contentId + "/chat", chatDto);
        });
    }

    @MessageMapping("/conversations/{conversationId}/direct-messages")
    public void handleDirectMessage(
            @DestinationVariable UUID conversationId,
            DirectMessageSendRequest request,
            Principal principal
    ) {
        String email = getEmailFromPrincipal(principal);
        if (email == null) return;

        userRepository.findByEmail(email).ifPresent(user -> {
            // 대화 메시지 생성 및 영속화 (ConversationService 내부에서 DB 저장 및 Dto 반환)
            DirectMessageDto messageDto = conversationService.saveDirectMessage(conversationId, user.getId(), request);

            log.info("[WebSocket DM] Conv={}: sender={}, receiver={}", conversationId, user.getId(), messageDto.receiver().userId());
            
            // 1. WebSocket 구독 중인 채널로 브로드캐스트
            messagingTemplate.convertAndSend("/sub/conversations/" + conversationId + "/direct-messages", messageDto);
            
            // 2. 수신자에게 실시간 SSE 알림 전송
            UUID receiverId = messageDto.receiver().userId();
            sseService.send(receiverId, messageDto, "direct-messages");
        });
    }

    @MessageMapping("/contents/{contentId}/watch/heartbeat")
    public void handleWatchingHeartbeat(
            @DestinationVariable UUID contentId,
            Principal principal
    ) {
        String email = getEmailFromPrincipal(principal);
        if (email != null) {
            watchingSessionService.touchSession(email, contentId);
        }
    }

    private String getEmailFromPrincipal(Principal principal) {
        if (principal instanceof Authentication) {
            Object principalObj = ((Authentication) principal).getPrincipal();
            if (principalObj instanceof UserDetails) {
                return ((UserDetails) principalObj).getUsername();
            } else if (principalObj instanceof String) {
                return (String) principalObj;
            }
        }
        return principal != null ? principal.getName() : null;
    }
}
