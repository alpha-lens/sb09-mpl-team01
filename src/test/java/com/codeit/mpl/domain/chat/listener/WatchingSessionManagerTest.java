package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.service.ContentService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
}
