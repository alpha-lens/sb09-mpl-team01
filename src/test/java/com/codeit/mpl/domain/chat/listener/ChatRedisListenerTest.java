package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.controller.WebHooks.ChatMessage;
import com.codeit.mpl.domain.chat.dto.RedisChatEvent;
import com.codeit.mpl.domain.content.dto.ContentChatDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.infra.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRedisListenerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Mock
    private ChannelTopic chatTopic;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private SseService sseService;

    @InjectMocks
    private ChatRedisListener chatRedisListener;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

    @Test
    @DisplayName("init 호출 시 listenerContainer에 리스너를 등록한다")
    void init() {
        chatRedisListener.init();
        verify(redisMessageListenerContainer).addMessageListener(chatRedisListener, chatTopic);
    }

    @Test
    @DisplayName("eventType 이 DM인 RedisChatEvent 수신 시 WebSocket과 SSE로 메시지를 전송한다")
    void onMessage_DM() throws Exception {
        UUID receiverId = UUID.randomUUID();
        UserSummary sender = new UserSummary(UUID.randomUUID(), "sender", "senderPic");
        UserSummary receiver = new UserSummary(receiverId, "receiver", "receiverPic");
        DirectMessageDto dmDto = new DirectMessageDto(UUID.randomUUID(), UUID.randomUUID(), Instant.now(), sender, receiver, "hello");

        RedisChatEvent event = new RedisChatEvent("DM", "/topic/dm", sender.userId(), receiverId, dmDto);
        String json = objectMapper.writeValueAsString(event);

        Message message = new DefaultMessage("chat-topic".getBytes(), json.getBytes());

        chatRedisListener.onMessage(message, null);

        verify(messagingTemplate).convertAndSend(eq("/topic/dm"), any(DirectMessageDto.class));
        verify(sseService).sendLocal(eq(receiverId), any(DirectMessageDto.class), eq("direct-messages"));
    }

    @Test
    @DisplayName("eventType 이 CONTENT_CHAT인 RedisChatEvent 수신 시 WebSocket으로 메시지를 전송한다")
    void onMessage_ContentChat() throws Exception {
        UserSummary sender = new UserSummary(UUID.randomUUID(), "sender", "senderPic");
        ContentChatDto contentDto = new ContentChatDto(UUID.randomUUID(), UUID.randomUUID(), sender, "msg", Instant.now());
        RedisChatEvent event = new RedisChatEvent("CONTENT_CHAT", "/topic/content/1", sender.userId(), null, contentDto);
        String json = objectMapper.writeValueAsString(event);

        Message message = new DefaultMessage("chat-topic".getBytes(), json.getBytes());

        chatRedisListener.onMessage(message, null);

        verify(messagingTemplate).convertAndSend(eq("/topic/content/1"), any(ContentChatDto.class));
    }

    @Test
    @DisplayName("eventType이 없는 일반 ChatMessage 수신 시 지정된 룸 토픽으로 전송한다")
    void onMessage_PlainChatMessage() throws Exception {
        ChatMessage chatMessage = new ChatMessage("room123", "sender1", "hi");
        String json = objectMapper.writeValueAsString(chatMessage);

        Message message = new DefaultMessage("chat-topic".getBytes(), json.getBytes());

        chatRedisListener.onMessage(message, null);

        verify(messagingTemplate).convertAndSend(eq("/topic/chat/room/room123"), any(ChatMessage.class));
    }

    @Test
    @DisplayName("역직렬화 실패 시 예외를 포획하고 로깅한다")
    void onMessage_Exception() {
        Message message = new DefaultMessage("chat-topic".getBytes(), "invalid json".getBytes());

        chatRedisListener.onMessage(message, null);

        verifyNoInteractions(messagingTemplate);
        verifyNoInteractions(sseService);
    }
}
