package com.codeit.mpl.domain.chat.controller;

import com.codeit.mpl.domain.chat.dto.RedisChatEvent;
import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.ContentChatDto;
import com.codeit.mpl.domain.content.dto.ContentChatSendRequest;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageSendRequest;
import com.codeit.mpl.domain.conversation.service.ConversationService;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.sse.SseService;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebsocketControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConversationService conversationService;

    @Mock
    private SseService sseService;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @Mock
    private WatchingSessionService watchingSessionService;

    @Mock
    private BinaryContentStorage binaryContentStorage;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ChannelTopic chatTopic;

    @InjectMocks
    private WebsocketController websocketController;

    @Test
    @DisplayName("handleContentChat - RedisTemplate이 없을 때 SimpMessagingTemplate으로 전송한다")
    void handleContentChat_withoutRedis_sendsViaMessagingTemplate() {
        // given
        UUID contentId = UUID.randomUUID();
        ContentChatSendRequest request = new ContentChatSendRequest("안녕하세요");

        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);
        given(authentication.getPrincipal()).willReturn(userDetails);
        given(userDetails.getUsername()).willReturn("test@mopl.io");

        User user = mock(User.class);
        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getName()).willReturn("테스트유저");
        given(user.getProfileImageUrl()).willReturn("profile.png");
        given(binaryContentStorage.getUrl("profile.png")).willReturn("http://s3/profile.png");
        given(userRepository.findByEmail("test@mopl.io")).willReturn(Optional.of(user));

        // when
        websocketController.handleContentChat(contentId, request, authentication);

        // then
        verify(messagingTemplate).convertAndSend(eq("/sub/contents/" + contentId + "/chat"), any(ContentChatDto.class));
    }

    @Test
    @DisplayName("handleContentChat - RedisTemplate 및 chatTopic이 존재할 때 Redis convertAndSend를 호출한다")
    void handleContentChat_withRedis_sendsViaRedisTemplate() {
        // given
        ReflectionTestUtils.setField(websocketController, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(websocketController, "chatTopic", chatTopic);

        UUID contentId = UUID.randomUUID();
        ContentChatSendRequest request = new ContentChatSendRequest("반갑습니다");

        Authentication authentication = mock(Authentication.class);
        given(authentication.getPrincipal()).willReturn("test@mopl.io");

        User user = mock(User.class);
        given(user.getId()).willReturn(UUID.randomUUID());
        given(user.getName()).willReturn("테스트유저");
        given(user.getProfileImageUrl()).willReturn(null);
        given(userRepository.findByEmail("test@mopl.io")).willReturn(Optional.of(user));
        given(chatTopic.getTopic()).willReturn("ch-chat");

        // when
        websocketController.handleContentChat(contentId, request, authentication);

        // then
        verify(redisTemplate).convertAndSend(eq("ch-chat"), any(RedisChatEvent.class));
    }

    @Test
    @DisplayName("handleDirectMessage - RedisTemplate이 없을 때 MessagingTemplate과 SseService로 전송한다")
    void handleDirectMessage_withoutRedis_sendsViaMessagingTemplateAndSse() {
        // given
        UUID conversationId = UUID.randomUUID();
        DirectMessageSendRequest request = new DirectMessageSendRequest("DM 메시지");

        Principal principal = () -> "test@mopl.io";

        User user = mock(User.class);
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        given(user.getId()).willReturn(senderId);
        given(userRepository.findByEmail("test@mopl.io")).willReturn(Optional.of(user));

        UserSummary receiverSummary = new UserSummary(receiverId, "수신자", null);
        DirectMessageDto messageDto = mock(DirectMessageDto.class);
        given(messageDto.receiver()).willReturn(receiverSummary);

        given(conversationService.saveDirectMessage(conversationId, senderId, request)).willReturn(messageDto);

        // when
        websocketController.handleDirectMessage(conversationId, request, principal);

        // then
        verify(messagingTemplate).convertAndSend(eq("/sub/conversations/" + conversationId + "/direct-messages"), eq(messageDto));
        verify(sseService).send(receiverId, messageDto, "direct-messages");
    }

    @Test
    @DisplayName("handleDirectMessage - RedisTemplate 존재 시 Redis convertAndSend를 호출한다")
    void handleDirectMessage_withRedis_sendsViaRedis() {
        // given
        ReflectionTestUtils.setField(websocketController, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(websocketController, "chatTopic", chatTopic);

        UUID conversationId = UUID.randomUUID();
        DirectMessageSendRequest request = new DirectMessageSendRequest("DM 메시지 2");

        Principal principal = () -> "test@mopl.io";

        User user = mock(User.class);
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();
        given(user.getId()).willReturn(senderId);
        given(userRepository.findByEmail("test@mopl.io")).willReturn(Optional.of(user));

        UserSummary receiverSummary = new UserSummary(receiverId, "수신자", null);
        DirectMessageDto messageDto = mock(DirectMessageDto.class);
        given(messageDto.receiver()).willReturn(receiverSummary);
        given(chatTopic.getTopic()).willReturn("ch-chat");

        given(conversationService.saveDirectMessage(conversationId, senderId, request)).willReturn(messageDto);

        // when
        websocketController.handleDirectMessage(conversationId, request, principal);

        // then
        verify(redisTemplate).convertAndSend(eq("ch-chat"), any(RedisChatEvent.class));
    }

    @Test
    @DisplayName("handleWatchingHeartbeat - 유효한 principal인 경우 touchSession을 호출한다")
    void handleWatchingHeartbeat_callsTouchSession() {
        // given
        UUID contentId = UUID.randomUUID();
        Principal principal = () -> "test@mopl.io";

        // when
        websocketController.handleWatchingHeartbeat(contentId, principal);

        // then
        verify(watchingSessionService).touchSession("test@mopl.io", contentId);
    }
}
