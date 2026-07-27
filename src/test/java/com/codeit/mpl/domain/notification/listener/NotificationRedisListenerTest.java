package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.infra.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationRedisListenerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Mock
    private ChannelTopic notificationTopic;

    @Mock
    private SseService sseService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private NotificationRedisListener notificationRedisListener;

    @Test
    @DisplayName("init 메서드 실행 시 Redis listener가 등록된다")
    void init() {
        notificationRedisListener.init();
        verify(redisMessageListenerContainer).addMessageListener(notificationRedisListener, notificationTopic);
    }

    @Test
    @DisplayName("onMessage 호출 시 정상 역직렬화 및 SSE 전송이 수행된다")
    void onMessage_Success() {
        // given
        UUID receiverId = UUID.randomUUID();
        String json = "{\"receiverId\":\"" + receiverId + "\",\"data\":\"test\"}";
        Message message = new DefaultMessage("ch-notification".getBytes(), json.getBytes());

        // when
        notificationRedisListener.onMessage(message, null);

        // then
        verify(sseService).send(eq(receiverId), eq("test"), eq("notifications"));
    }

    @Test
    @DisplayName("onMessage 호출 중 역직렬화 예외가 발생하면 로깅 후 예외가 전파되지 않는다")
    void onMessage_DeserializationError() {
        // given
        String json = "invalid json payload";
        Message message = new DefaultMessage("ch-notification".getBytes(), json.getBytes());

        // when
        notificationRedisListener.onMessage(message, null);

        // then
        verifyNoInteractions(sseService);
    }
}
