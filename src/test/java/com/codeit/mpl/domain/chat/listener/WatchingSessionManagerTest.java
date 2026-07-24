package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.WatchingSessionChange;
import com.codeit.mpl.domain.content.dto.WatchingSessionSnapshot;
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
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

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

    @InjectMocks
    private WatchingSessionManager watchingSessionManager;

    @Test
    @DisplayName("WatchingSessionManager initialization check")
    void initCheck() {
        assertThat(watchingSessionManager).isNotNull();
    }

    @Test
    @DisplayName("handleSubscribe - 구독 성공 시 JOIN 브로드캐스트 및 개인 큐 스냅샷 PUSH")
    void handleSubscribe_success() {
        UUID contentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        StompHeaderAccessor accessor = StompHeaderAccessor.create(org.springframework.messaging.simp.stomp.StompCommand.SUBSCRIBE);
        accessor.setDestination("/sub/contents/" + contentId + "/watch");
        accessor.setSessionId("session-1");
        accessor.setSubscriptionId("sub-1");
        accessor.setUser(new UsernamePasswordAuthenticationToken("user@test.com", "pass"));

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message);

        User user = User.builder().email("user@test.com").name("UserA").build();
        try {
            var field = com.codeit.mpl.infra.common.entity.base.BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (Exception ignored) {}

        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(contentService.getContent(contentId)).willReturn(null);
        WatchingSessionSnapshot snapshot = new WatchingSessionSnapshot(List.of(), 1L);
        given(watchingSessionService.getActiveWatcherSnapshot(contentId)).willReturn(snapshot);

        watchingSessionManager.handleSubscribe(event);

        verify(watchingSessionService).registerSession(userId, contentId);
        verify(messagingTemplate).convertAndSend(eq("/sub/contents/" + contentId + "/watch"), any(WatchingSessionChange.class));
        verify(messagingTemplate).convertAndSendToUser(eq("user@test.com"), eq("/queue/contents/" + contentId + "/watch-snapshot"), eq(snapshot));
    }
}
