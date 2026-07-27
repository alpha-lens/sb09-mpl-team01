package com.codeit.mpl.domain.notification.listener;

import com.codeit.mpl.domain.notification.dto.NotificationDto;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.infra.sse.SseService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationAsyncHandlerTest {

    @Mock
    private NotificationRetryHandler notificationRetryHandler;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SseService sseService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private NotificationAsyncHandler notificationAsyncHandler;

    private User createUser(UUID id) {
        User user = User.builder().email("test@example.com").build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    @DisplayName("NotificationEvent 처리 성공 시 Redis Pub/Sub으로 메시지가 전송된다")
    void process_Success() throws Exception {
        // given
        UUID receiverId = UUID.randomUUID();
        User receiver = createUser(receiverId);
        NotificationEvent event = new NotificationEvent(receiver, null, NotificationLevel.INFO, "Title", "Content", null, null);
        NotificationDto dto = new NotificationDto(UUID.randomUUID(), Instant.now(), receiverId, "Title", "Content", NotificationLevel.INFO);

        given(notificationRetryHandler.saveWithRetry(event)).willReturn(dto);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");

        // when
        notificationAsyncHandler.process(event);

        // then
        verify(redisTemplate).convertAndSend(eq("ch-notification"), eq("{}"));
    }

    @Test
    @DisplayName("DB 저장 실패 시 알림 처리가 중단된다")
    void process_DbSaveFailure() {
        // given
        UUID receiverId = UUID.randomUUID();
        User receiver = createUser(receiverId);
        NotificationEvent event = new NotificationEvent(receiver, null, NotificationLevel.INFO, "Title", "Content", null, null);

        given(notificationRetryHandler.saveWithRetry(event)).willThrow(new RuntimeException("DB error"));

        // when
        notificationAsyncHandler.process(event);

        // then
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(sseService);
    }

    @Test
    @DisplayName("Redis 직렬화 실패 시 SseService 로컬 폴백이 호출된다")
    void process_JsonProcessingExceptionFallback() throws Exception {
        // given
        UUID receiverId = UUID.randomUUID();
        User receiver = createUser(receiverId);
        NotificationEvent event = new NotificationEvent(receiver, null, NotificationLevel.INFO, "Title", "Content", null, null);
        NotificationDto dto = new NotificationDto(UUID.randomUUID(), Instant.now(), receiverId, "Title", "Content", NotificationLevel.INFO);

        given(notificationRetryHandler.saveWithRetry(event)).willReturn(dto);
        given(objectMapper.writeValueAsString(any())).willThrow(new JsonProcessingException("JSON error") {});

        // when
        notificationAsyncHandler.process(event);

        // then
        verify(sseService).sendLocal(receiverId, dto, "notifications");
    }

    @Test
    @DisplayName("Redis 전송 에러 시 SseService 로컬 폴백이 호출된다")
    void process_RedisExceptionFallback() throws Exception {
        // given
        UUID receiverId = UUID.randomUUID();
        User receiver = createUser(receiverId);
        NotificationEvent event = new NotificationEvent(receiver, null, NotificationLevel.INFO, "Title", "Content", null, null);
        NotificationDto dto = new NotificationDto(UUID.randomUUID(), Instant.now(), receiverId, "Title", "Content", NotificationLevel.INFO);

        given(notificationRetryHandler.saveWithRetry(event)).willReturn(dto);
        given(objectMapper.writeValueAsString(any())).willReturn("{}");
        doThrow(new RuntimeException("Redis connection error")).when(redisTemplate).convertAndSend(anyString(), anyString());

        // when
        notificationAsyncHandler.process(event);

        // then
        verify(sseService).sendLocal(receiverId, dto, "notifications");
    }
}
