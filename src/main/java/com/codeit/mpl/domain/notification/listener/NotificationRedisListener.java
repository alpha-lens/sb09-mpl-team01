package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.infra.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class NotificationRedisListener implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ChannelTopic notificationTopic;
    private final SseService sseService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, notificationTopic);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String topic = new String(message.getChannel());
        String body = RedisSerializer.string().deserialize(message.getBody());
        
        log.info("[Redis Notification] 메시지 수신 성공. topic={}, body={}", topic, body);

        try {
            RedisNotificationWrapper notification = objectMapper.readValue(body, RedisNotificationWrapper.class);
            sseService.send(notification.getReceiverId(), notification.getData(), "notifications");
        } catch (IOException e) {
            log.error("[Redis Notification] 메시지 역직렬화 및 전송 실패", e);
        }
    }

    public static class RedisNotificationWrapper {
        private UUID receiverId;
        private Object data;

        public RedisNotificationWrapper() {}

        public RedisNotificationWrapper(UUID receiverId, Object data) {
            this.receiverId = receiverId;
            this.data = data;
        }

        public UUID getReceiverId() {
            return receiverId;
        }

        public void setReceiverId(UUID receiverId) {
            this.receiverId = receiverId;
        }

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }
}
