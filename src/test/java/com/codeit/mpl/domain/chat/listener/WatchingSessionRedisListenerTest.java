package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.dto.RedisWatchingSessionEvent;
import com.codeit.mpl.domain.content.dto.ChangeType;
import com.codeit.mpl.domain.content.dto.WatchingSessionChange;
import com.codeit.mpl.domain.content.dto.WatchingSessionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchingSessionRedisListenerTest {

    @Mock
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Mock
    private ChannelTopic watchingSessionTopic;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private WatchingSessionRedisListener watchingSessionRedisListener;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("init 메서드 호출 시 listener가 컨테이너에 등록된다")
    void init_registersMessageListener() {
        watchingSessionRedisListener.init();
        verify(redisMessageListenerContainer).addMessageListener(watchingSessionRedisListener, watchingSessionTopic);
    }

    @Test
    @DisplayName("onMessage - SNAPSHOT 이벤트를 수신하면 user별로 convertAndSendToUser를 호출한다")
    void onMessage_snapshotEvent_sendsToUser() throws Exception {
        // given
        WatchingSessionSnapshot snapshot = new WatchingSessionSnapshot(List.of(), 0L);
        String snapshotJson = objectMapper.writeValueAsString(snapshot);
        RedisWatchingSessionEvent event = new RedisWatchingSessionEvent("SNAPSHOT", UUID.randomUUID(), "user@mopl.io", "/queue/snapshot", snapshotJson);
        String body = objectMapper.writeValueAsString(event);

        Message message = mock(Message.class);
        given(message.getChannel()).willReturn("ch-watching-session".getBytes(StandardCharsets.UTF_8));
        given(message.getBody()).willReturn(body.getBytes(StandardCharsets.UTF_8));

        // when
        watchingSessionRedisListener.onMessage(message, null);

        // then
        verify(messagingTemplate).convertAndSendToUser(eq("user@mopl.io"), eq("/queue/snapshot"), any(WatchingSessionSnapshot.class));
    }

    @Test
    @DisplayName("onMessage - JOIN 및 LEAVE 이벤트를 수신하면 convertAndSend를 호출한다")
    void onMessage_joinEvent_sendsToDestination() throws Exception {
        // given
        WatchingSessionChange change = new WatchingSessionChange(ChangeType.JOIN, null, 1L);
        String changeJson = objectMapper.writeValueAsString(change);
        RedisWatchingSessionEvent event = new RedisWatchingSessionEvent("JOIN", UUID.randomUUID(), null, "/sub/contents/123/watch", changeJson);
        String body = objectMapper.writeValueAsString(event);

        Message message = mock(Message.class);
        given(message.getChannel()).willReturn("ch-watching-session".getBytes(StandardCharsets.UTF_8));
        given(message.getBody()).willReturn(body.getBytes(StandardCharsets.UTF_8));

        // when
        watchingSessionRedisListener.onMessage(message, null);

        // then
        verify(messagingTemplate).convertAndSend(eq("/sub/contents/123/watch"), any(WatchingSessionChange.class));
    }
}
