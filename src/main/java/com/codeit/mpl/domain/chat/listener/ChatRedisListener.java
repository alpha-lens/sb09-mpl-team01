package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.controller.WebHooks.ChatMessage;
import com.codeit.mpl.domain.chat.dto.RedisChatEvent;
import com.codeit.mpl.domain.content.dto.ContentChatDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.infra.sse.SseService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class ChatRedisListener implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ChannelTopic chatTopic;
    private final SimpMessageSendingOperations messagingTemplate;
    private final SseService sseService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, chatTopic);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String topic = new String(message.getChannel());
        String body = RedisSerializer.string().deserialize(message.getBody());
        
        log.info("[Redis Chat] 메시지 수신 성공. topic={}, body={}", topic, body);

        try {
            if (body != null && body.contains("\"eventType\"")) {
                RedisChatEvent chatEvent = objectMapper.readValue(body, RedisChatEvent.class);
                if ("DM".equals(chatEvent.eventType())) {
                    DirectMessageDto dmDto = objectMapper.convertValue(chatEvent.payload(), DirectMessageDto.class);
                    messagingTemplate.convertAndSend(chatEvent.destination(), dmDto);
                    if (chatEvent.receiverId() != null) {
                        sseService.sendLocal(chatEvent.receiverId(), dmDto, "direct-messages");
                    }
                } else if ("CONTENT_CHAT".equals(chatEvent.eventType())) {
                    ContentChatDto contentChatDto = objectMapper.convertValue(chatEvent.payload(), ContentChatDto.class);
                    messagingTemplate.convertAndSend(chatEvent.destination(), contentChatDto);
                }
            } else {
                ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
                messagingTemplate.convertAndSend("/topic/chat/room/" + chatMessage.getRoomId(), chatMessage);
            }
        } catch (Exception e) {
            log.error("[Redis Chat] 메시지 역직렬화 및 전송 실패", e);
        }
    }
}
