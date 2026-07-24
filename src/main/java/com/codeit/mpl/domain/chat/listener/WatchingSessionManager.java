package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.dto.RedisWatchingSessionEvent;
import com.codeit.mpl.domain.chat.service.WatchingSessionService;
import com.codeit.mpl.domain.content.dto.ChangeType;
import com.codeit.mpl.domain.content.dto.WatchingSessionChange;
import com.codeit.mpl.domain.content.dto.WatchingSessionDto;
import com.codeit.mpl.domain.content.dto.WatchingSessionSnapshot;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchingSessionManager {

    private final UserRepository userRepository;
    private final SimpMessageSendingOperations messagingTemplate;
    private final WatchingSessionService watchingSessionService;
    private final ContentService contentService;
    private final com.codeit.mpl.infra.storage.BinaryContentStorage binaryContentStorage;

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Autowired(required = false)
    @Qualifier("watchingSessionTopic")
    private ChannelTopic watchingSessionTopic;

    // key: sessionId_subscriptionId
    private final Map<String, WatchingSessionDto> sessionMap = new ConcurrentHashMap<>();
    // key: contentId, value: Map of (sessionId_subscriptionId -> WatchingSessionDto)
    private final Map<UUID, Map<String, WatchingSessionDto>> contentWatchers = new ConcurrentHashMap<>();

    private static final Pattern WATCH_PATTERN = Pattern.compile("^/sub/contents/([a-fA-F0-9\\-]+)/watch$");

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null) return;

        Matcher matcher = WATCH_PATTERN.matcher(destination);
        if (matcher.matches()) {
            UUID contentId = UUID.fromString(matcher.group(1));
            String sessionId = accessor.getSessionId();
            String subscriptionId = accessor.getSubscriptionId();
            String key = sessionId + "_" + subscriptionId;

            Principal principal = accessor.getUser();
            if (principal instanceof Authentication) {
                Object principalObj = ((Authentication) principal).getPrincipal();
                String email = null;
                if (principalObj instanceof UserDetails) {
                    email = ((UserDetails) principalObj).getUsername();
                } else if (principalObj instanceof String) {
                    email = (String) principalObj;
                }

                if (email != null) {
                    final String userEmail = email;
                    userRepository.findByEmail(email).ifPresent(user -> {
                        String resolvedImageUrl = user.getProfileImageUrl() != null
                                ? binaryContentStorage.getUrl(user.getProfileImageUrl())
                                : null;
                        UserSummary userSummary = new UserSummary(user.getId(), user.getName(), resolvedImageUrl);
                        ContentDto contentDto = contentService.getContent(contentId);
                        WatchingSessionDto watchingSession = new WatchingSessionDto(user.getId(), Instant.now(), userSummary, contentDto);

                        sessionMap.put(key, watchingSession);
                        contentWatchers.computeIfAbsent(contentId, k -> new ConcurrentHashMap<>()).put(key, watchingSession);

                        // Redis에 먼저 등록
                        watchingSessionService.registerSession(user.getId(), contentId);

                        // 등록 후 나를 포함한 최신 전체 스냅샷 조회
                        WatchingSessionSnapshot snapshot = watchingSessionService.getActiveWatcherSnapshot(contentId);
                        WatchingSessionChange change = new WatchingSessionChange(ChangeType.JOIN, watchingSession, snapshot.totalCount());

                        log.info("[WebSocket Session] JOIN: contentId={}, userId={}, count={}", contentId, user.getId(), snapshot.totalCount());

                        // 1) 다른 사람들에게 나의 JOIN 전파
                        publishOrSend("JOIN", contentId, null, "/sub/contents/" + contentId + "/watch", change);
                        // 2) 신규 접속자 본인에게는 나를 포함한 전체 스냅샷 전송 (로컬 직접 전송 우선)
                        messagingTemplate.convertAndSendToUser(userEmail, "/queue/contents/" + contentId + "/watch-snapshot", snapshot);
                    });
                }
            }
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String key = sessionId + "_" + subscriptionId;

        processLeave(key);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        
        sessionMap.keySet().stream()
                .filter(key -> key.startsWith(sessionId + "_") || key.equals(sessionId))
                .toList()
                .forEach(this::processLeave);
    }

    private void processLeave(String key) {
        WatchingSessionDto watchingSession = sessionMap.remove(key);
        if (watchingSession != null) {
            UUID contentId = watchingSession.content().id();
            Map<String, WatchingSessionDto> watchers = contentWatchers.get(contentId);
            if (watchers != null) {
                watchers.remove(key);
            }
            watchingSessionService.removeSession(watchingSession.watcher().userId());
            long watcherCount = watchingSessionService.getWatcherCount(contentId);
            WatchingSessionChange change = new WatchingSessionChange(ChangeType.LEAVE, watchingSession, watcherCount);

            log.info("[WebSocket Session] LEAVE: contentId={}, userId={}, count={}", contentId, watchingSession.watcher().userId(), watcherCount);
            publishOrSend("LEAVE", contentId, null, "/sub/contents/" + contentId + "/watch", change);
        }
    }

    private void publishOrSend(String eventType, UUID contentId, String userEmail, String destination, Object payload) {
        if (redisTemplate != null && watchingSessionTopic != null) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

                String payloadJson = mapper.writeValueAsString(payload);
                RedisWatchingSessionEvent event = new RedisWatchingSessionEvent(eventType, contentId, userEmail, destination, payloadJson);
                redisTemplate.convertAndSend(watchingSessionTopic.getTopic(), event);
            } catch (Exception e) {
                log.error("[WatchingSessionManager] Error serializing payload", e);
            }
        } else {
            if ("SNAPSHOT".equals(eventType) && userEmail != null) {
                messagingTemplate.convertAndSendToUser(userEmail, destination, payload);
            } else {
                messagingTemplate.convertAndSend(destination, payload);
            }
        }
    }
}

