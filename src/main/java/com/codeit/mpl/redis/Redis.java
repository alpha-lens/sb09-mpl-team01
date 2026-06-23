package com.codeit.mpl.redis;

import com.codeit.mpl.sse.SseService;
import com.codeit.mpl.websocket.WebHooks.ChatMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Redis implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ChannelTopic notificationTopic;
    private final ChannelTopic chatTopic;
    private final SseService sseService;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Registers this service as a listener for notification and chat Redis topics.
     */
    @PostConstruct
    public void init() {
        // 리스너 컨테이너에 현재 클래스(MessageListener)를 구독 등록
        redisMessageListenerContainer.addMessageListener(this, notificationTopic);
        redisMessageListenerContainer.addMessageListener(this, chatTopic);
    }

    /**
     * Publishes a message to a Redis channel.
     *
     * @param topic the topic to publish to
     * @param message the message to publish
     */
    public void publish(ChannelTopic topic, Object message) {
        redisTemplate.convertAndSend(topic.getTopic(), message);
        log.info("[Redis] 메시지 발행 성공. topic={}, message={}", topic.getTopic(), message);
    }

    /**
     * Handles incoming Redis pub/sub messages by routing them to appropriate handlers based on topic.
     *
     * Notification messages are forwarded to the SSE service, and chat messages are broadcast
     * via STOMP to the respective chat room.
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String topic = new String(message.getChannel());
        String body = (String) redisTemplate.getValueSerializer().deserialize(message.getBody());
        
        log.info("[Redis] 메시지 수신 성공. topic={}, body={}", topic, body);

        try {
            if (topic.equals(notificationTopic.getTopic())) {
                // 1. 알림 전파: JSON -> 알림 객체 파싱 후 SSE Emitter로 발송
                RedisNotificationWrapper notification = objectMapper.readValue(body, RedisNotificationWrapper.class);
                sseService.send(notification.getReceiverId(), notification.getData(), "notification");
                
            } else if (topic.equals(chatTopic.getTopic())) {
                // 2. 채팅 전파: JSON -> ChatMessage 파싱 후 WebSocket STOMP 브로드캐스트
                ChatMessage chatMessage = objectMapper.readValue(body, ChatMessage.class);
                messagingTemplate.convertAndSend("/topic/chat/room/" + chatMessage.getRoomId(), chatMessage);
            }
        } catch (IOException e) {
            log.error("[Redis] 메시지 역직렬화 및 전송 실패", e);
        }
    }

    /**
     * 알림 전송을 위한 래퍼 클래스
     */
    public static class RedisNotificationWrapper {
        private UUID receiverId;
        private Object data;

        /**
 * Constructs an empty notification wrapper.
 */
public RedisNotificationWrapper() {}

        /**
         * Constructs a notification wrapper with the specified recipient and payload.
         *
         * @param receiverId the UUID of the recipient
         * @param data       the notification payload
         */
        public RedisNotificationWrapper(UUID receiverId, Object data) {
            this.receiverId = receiverId;
            this.data = data;
        }

        /**
         * Returns the receiver ID.
         *
         * @return the receiver ID
         */
        public UUID getReceiverId() {
            return receiverId;
        }

        public void setReceiverId(UUID receiverId) {
            this.receiverId = receiverId;
        }

        public Object getData() {
            return data;
        }

        /**
         * Sets the notification data payload.
         *
         * @param data the data to store
         */
        public void setData(Object data) {
            this.data = data;
        }
    }
}
