package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.dto.RedisWatchingSessionEvent;
import com.codeit.mpl.domain.content.dto.WatchingSessionChange;
import com.codeit.mpl.domain.content.dto.WatchingSessionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class WatchingSessionRedisListener implements MessageListener {

    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final ChannelTopic watchingSessionTopic;
    private final SimpMessageSendingOperations messagingTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    @PostConstruct
    public void init() {
        redisMessageListenerContainer.addMessageListener(this, watchingSessionTopic);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String topic = new String(message.getChannel());
        String body = RedisSerializer.string().deserialize(message.getBody());

        log.debug("[Redis WatchingSession] Event received. topic={}, body={}", topic, body);

        try {
            if (body != null) {
                RedisWatchingSessionEvent event = objectMapper.readValue(body, RedisWatchingSessionEvent.class);
                if ("SNAPSHOT".equals(event.eventType())) {
                    if (event.payloadJson() != null) {
                        WatchingSessionSnapshot snapshot = objectMapper.readValue(event.payloadJson(), WatchingSessionSnapshot.class);
                        if (event.userEmail() != null) {
                            messagingTemplate.convertAndSendToUser(
                                    event.userEmail(),
                                    event.destination(),
                                    snapshot
                            );
                        }
                    }
                } else if ("JOIN".equals(event.eventType()) || "LEAVE".equals(event.eventType())) {
                    if (event.payloadJson() != null) {
                        WatchingSessionChange change = objectMapper.readValue(event.payloadJson(), WatchingSessionChange.class);
                        messagingTemplate.convertAndSend(event.destination(), change);
                    }
                }
            }
        } catch (Exception e) {
            log.error("[Redis WatchingSession] Error processing message", e);
        }
    }
}
