package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.dto.RedisWatchingSessionEvent;
import com.codeit.mpl.domain.content.dto.ChangeType;
import com.codeit.mpl.domain.content.dto.WatchingSessionChange;
import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WatchingSessionRedisListenerTest {

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private WatchingSessionRedisListener watchingSessionRedisListener;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("onMessage - JOIN 이벤트 수신 시 JSON 역직렬화 오류 없이 STOMP로 브로드캐스트 성공")
    void onMessage_join_success() throws Exception {
        UUID contentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UserSummary userSummary = new UserSummary(userId, "TestUser", "http://image.url");
        ContentDto contentDto = new ContentDto(
                contentId,
                com.codeit.mpl.domain.content.entity.ContentType.MOVIE,
                "Test Content",
                "Description",
                "http://thumb.url",
                List.of("tag1", "tag2"),
                4.5,
                10,
                1L
        );

        WatchingSessionDto sessionDto = new WatchingSessionDto(userId, Instant.now(), userSummary, contentDto);
        WatchingSessionChange change = new WatchingSessionChange(ChangeType.JOIN, sessionDto, 1L);

        String payloadJson = objectMapper.writeValueAsString(change);
        RedisWatchingSessionEvent event = new RedisWatchingSessionEvent("JOIN", contentId, null, "/sub/contents/" + contentId + "/watch", payloadJson);
        String eventJson = objectMapper.writeValueAsString(event);

        Message message = new DefaultMessage("ch-watching-session".getBytes(), eventJson.getBytes());

        watchingSessionRedisListener.onMessage(message, null);

        verify(messagingTemplate).convertAndSend(eq("/sub/contents/" + contentId + "/watch"), eq(change));
    }
}
