package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.dto.RedisWatchingSessionEvent;
import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.WatchingSessionChange;
import com.codeit.mpl.domain.content.dto.WatchingSessionSnapshot;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WatchingSessionManagerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private WatchingSessionService watchingSessionService;

    @Mock
    private ContentService contentService;

    @Mock
    private BinaryContentStorage binaryContentStorage;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChannelTopic watchingSessionTopic;

    @InjectMocks
    private WatchingSessionManager watchingSessionManager;

    @Test
    @DisplayName("handleSubscribe - 시청 구독 경로가 매칭되고 유저 정보가 있을 때 세션 등록 및 JOIN 이벤트를 전송한다")
    void handleSubscribe_success() {
        // given
        UUID contentId = UUID.randomUUID();
        String destination = "/sub/contents/" + contentId + "/watch";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId("session123");
        accessor.setSubscriptionId("sub456");

        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        given(authentication.getPrincipal()).willReturn(userDetails);
        given(userDetails.getUsername()).willReturn("user@mopl.io");
        accessor.setUser(authentication);

        Message<byte[]> message = mock(Message.class);
        given(message.getHeaders()).willReturn(accessor.getMessageHeaders());
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        given(user.getId()).willReturn(userId);
        given(user.getName()).willReturn("홍길동");
        given(user.getProfileImageUrl()).willReturn("img.png");
        given(binaryContentStorage.getUrl("img.png")).willReturn("http://s3/img.png");
        given(userRepository.findByEmail("user@mopl.io")).willReturn(Optional.of(user));

        ContentDto contentDto = mock(ContentDto.class);
        given(contentService.getContent(contentId)).willReturn(contentDto);

        WatchingSessionSnapshot snapshot = new WatchingSessionSnapshot(List.of(), 1L);
        given(watchingSessionService.getActiveWatcherSnapshot(eq(contentId))).willReturn(snapshot);

        // when
        watchingSessionManager.handleSubscribe(event);

        // then
        verify(watchingSessionService).registerSession(userId, contentId);
        verify(messagingTemplate).convertAndSend(eq(destination), any(WatchingSessionChange.class));
        verify(messagingTemplate).convertAndSendToUser(eq("user@mopl.io"), eq("/queue/contents/" + contentId + "/watch-snapshot"), eq(snapshot));
    }

    @Test
    @DisplayName("handleSubscribe - destination이 null이거나 시청 경로 패턴이 아니면 아무 작업도 하지 않는다")
    void handleSubscribe_ignoredPath() {
        // given
        StompHeaderAccessor accessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/chat");
        Message<byte[]> message = mock(Message.class);
        given(message.getHeaders()).willReturn(accessor.getMessageHeaders());
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        // when
        watchingSessionManager.handleSubscribe(event);

        // then
        verify(watchingSessionService, never()).registerSession(any(), any());
    }

    @Test
    @DisplayName("handleSubscribe - RedisTemplate이 존재하는 경우 Redis convertAndSend를 호출한다")
    void handleSubscribe_withRedis() {
        // given
        ReflectionTestUtils.setField(watchingSessionManager, "watchingSessionTopic", watchingSessionTopic);
        given(watchingSessionTopic.getTopic()).willReturn("ch-watching-session");

        UUID contentId = UUID.randomUUID();
        String destination = "/sub/contents/" + contentId + "/watch";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setSessionId("session123");
        accessor.setSubscriptionId("sub456");

        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn("user@mopl.io");
        accessor.setUser(authentication);

        Message<byte[]> message = mock(Message.class);
        given(message.getHeaders()).willReturn(accessor.getMessageHeaders());
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        given(user.getId()).willReturn(userId);
        given(user.getName()).willReturn("홍길동");
        given(user.getProfileImageUrl()).willReturn(null);
        given(userRepository.findByEmail("user@mopl.io")).willReturn(Optional.of(user));

        ContentDto contentDto = mock(ContentDto.class);
        given(contentService.getContent(contentId)).willReturn(contentDto);

        WatchingSessionSnapshot snapshot = new WatchingSessionSnapshot(List.of(), 1L);
        given(watchingSessionService.getActiveWatcherSnapshot(eq(contentId))).willReturn(snapshot);

        // when
        watchingSessionManager.handleSubscribe(event);

        // then
        verify(redisTemplate).convertAndSend(eq("ch-watching-session"), any(RedisWatchingSessionEvent.class));
    }

    @Test
    @DisplayName("handleUnsubscribe - 세션 구독 해제 시 LEAVE 처리 및 알림을 전파한다")
    void handleUnsubscribe_success() {
        // 1. 먼저 세션 등록 진행
        UUID contentId = UUID.randomUUID();
        String destination = "/sub/contents/" + contentId + "/watch";

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        subAccessor.setDestination(destination);
        subAccessor.setSessionId("session123");
        subAccessor.setSubscriptionId("sub456");

        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn("user@mopl.io");
        subAccessor.setUser(authentication);

        Message<byte[]> subMessage = mock(Message.class);
        given(subMessage.getHeaders()).willReturn(subAccessor.getMessageHeaders());
        SessionSubscribeEvent subEvent = new SessionSubscribeEvent(this, subMessage);

        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        given(user.getId()).willReturn(userId);
        given(user.getName()).willReturn("홍길동");
        given(userRepository.findByEmail("user@mopl.io")).willReturn(Optional.of(user));
        ContentDto contentDto1 = mock(ContentDto.class);
        given(contentDto1.id()).willReturn(contentId);
        given(contentService.getContent(contentId)).willReturn(contentDto1);
        given(watchingSessionService.getActiveWatcherSnapshot(eq(contentId))).willReturn(new WatchingSessionSnapshot(List.of(), 1L));

        watchingSessionManager.handleSubscribe(subEvent);

        // 2. handleUnsubscribe 호출
        StompHeaderAccessor unsubAccessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.UNSUBSCRIBE);
        unsubAccessor.setSessionId("session123");
        unsubAccessor.setSubscriptionId("sub456");
        Message<byte[]> unsubMessage = mock(Message.class);
        given(unsubMessage.getHeaders()).willReturn(unsubAccessor.getMessageHeaders());

        watchingSessionManager.handleUnsubscribe(new SessionUnsubscribeEvent(this, unsubMessage));

        // then
        verify(watchingSessionService).removeSession(userId);
    }

    @Test
    @DisplayName("handleDisconnect - WebSocket 연결 종료 시 해당 세션 ID에 등록된 세션들을 LEAVE 처리한다")
    void handleDisconnect_success() {
        // 1. 세션 등록 진행
        UUID contentId = UUID.randomUUID();
        String destination = "/sub/contents/" + contentId + "/watch";

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        subAccessor.setDestination(destination);
        subAccessor.setSessionId("session999");
        subAccessor.setSubscriptionId("sub888");

        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn("user2@mopl.io");
        subAccessor.setUser(authentication);

        Message<byte[]> subMessage = mock(Message.class);
        given(subMessage.getHeaders()).willReturn(subAccessor.getMessageHeaders());
        SessionSubscribeEvent subEvent = new SessionSubscribeEvent(this, subMessage);

        User user = mock(User.class);
        UUID userId = UUID.randomUUID();
        given(user.getId()).willReturn(userId);
        given(user.getName()).willReturn("이순신");
        given(userRepository.findByEmail("user2@mopl.io")).willReturn(Optional.of(user));
        ContentDto contentDto2 = mock(ContentDto.class);
        given(contentDto2.id()).willReturn(contentId);
        given(contentService.getContent(contentId)).willReturn(contentDto2);
        given(watchingSessionService.getActiveWatcherSnapshot(eq(contentId))).willReturn(new WatchingSessionSnapshot(List.of(), 1L));

        watchingSessionManager.handleSubscribe(subEvent);

        // 2. handleDisconnect 호출
        SessionDisconnectEvent disconnectEvent = mock(SessionDisconnectEvent.class);
        given(disconnectEvent.getSessionId()).willReturn("session999");
        watchingSessionManager.handleDisconnect(disconnectEvent);

        // then
        verify(watchingSessionService).removeSession(userId);
    }
}
