package com.codeit.mpl.domain.conversation.service;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ActiveConversationManagerTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActiveConversationManager activeConversationManager;

    private UUID userId;
    private UUID conversationId;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        conversationId = UUID.randomUUID();
        user = User.builder().email("test@example.com").name("Tester").build();

        Field idField = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);
    }

    @Test
    @DisplayName("handleSubscribe - DM 구독 시 활성 대화 상대 등록")
    void handleSubscribe_success() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/conversations/" + conversationId + "/direct-messages");
        accessor.setSessionId("session1");
        accessor.setSubscriptionId("sub1");
        accessor.setUser(new UsernamePasswordAuthenticationToken("test@example.com", ""));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        activeConversationManager.handleSubscribe(event);

        boolean active = activeConversationManager.isUserActiveInConversation(userId, conversationId);
        assertThat(active).isTrue();
    }

    @Test
    @DisplayName("handleUnsubscribe - 구독 해제 시 활성 세션 제거")
    void handleUnsubscribe_success() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/sub/conversations/" + conversationId + "/direct-messages");
        subAccessor.setSessionId("session1");
        subAccessor.setSubscriptionId("sub1");
        subAccessor.setUser(new UsernamePasswordAuthenticationToken("test@example.com", ""));

        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());
        activeConversationManager.handleSubscribe(new SessionSubscribeEvent(this, subMessage));

        StompHeaderAccessor unsubAccessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        unsubAccessor.setSessionId("session1");
        unsubAccessor.setSubscriptionId("sub1");

        Message<byte[]> unsubMessage = MessageBuilder.createMessage(new byte[0], unsubAccessor.getMessageHeaders());
        activeConversationManager.handleUnsubscribe(new SessionUnsubscribeEvent(this, unsubMessage));

        boolean active = activeConversationManager.isUserActiveInConversation(userId, conversationId);
        assertThat(active).isFalse();
    }

    @Test
    @DisplayName("handleDisconnect - 연결 해제 시 모든 관련 세션 제거")
    void handleDisconnect_success() {
        given(userRepository.findByEmail("test@example.com")).willReturn(Optional.of(user));

        StompHeaderAccessor subAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subAccessor.setDestination("/sub/conversations/" + conversationId + "/direct-messages");
        subAccessor.setSessionId("session1");
        subAccessor.setSubscriptionId("sub1");
        subAccessor.setUser(new UsernamePasswordAuthenticationToken("test@example.com", ""));

        Message<byte[]> subMessage = MessageBuilder.createMessage(new byte[0], subAccessor.getMessageHeaders());
        activeConversationManager.handleSubscribe(new SessionSubscribeEvent(this, subMessage));

        SessionDisconnectEvent disconnectEvent = new SessionDisconnectEvent(this, subMessage, "session1", null);
        activeConversationManager.handleDisconnect(disconnectEvent);

        boolean active = activeConversationManager.isUserActiveInConversation(userId, conversationId);
        assertThat(active).isFalse();
    }
}
