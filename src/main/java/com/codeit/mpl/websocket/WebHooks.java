package com.codeit.mpl.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebHooks {

    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Receives a chat message from a WebSocket client and broadcasts it to subscribers
     * of the corresponding chat room.
     *
     * @param message the chat message containing the room ID, sender, and content
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessage message) {
        log.info("[WebSocket] 실시간 채팅 메시지 인입: roomId={}, sender={}, message={}",
                message.getRoomId(), message.getSender(), message.getMessage());

        // 분산 환경의 기초: 수신한 메시지를 /topic/chat/room/{roomId} 구독자들에게 전달
        // 실제 구현에서는 이 시점에 Kafka로 보내 영속화하고, Redis Pub/Sub으로 다른 서버들에도 브로드캐스트합니다.
        messagingTemplate.convertAndSend("/topic/chat/room/" + message.getRoomId(), message);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String roomId;
        private String sender;
        private String message;
    }
}
