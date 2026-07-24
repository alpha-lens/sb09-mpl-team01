package com.codeit.mpl.domain.conversation.service;

import com.codeit.mpl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActiveConversationManager {

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String ACTIVE_CONVERSATION_KEY_PREFIX = "active_conv:";

    // key: sessionId_subscriptionId, value: UserConversationPair
    private final Map<String, UserConversationPair> sessionMap = new ConcurrentHashMap<>();

    private static final Pattern DM_PATTERN = Pattern.compile("^/sub/conversations/([a-fA-F0-9\\-]+)/direct-messages$");

    public boolean isUserActiveInConversation(UUID userId, UUID conversationId) {
        String key = ACTIVE_CONVERSATION_KEY_PREFIX + conversationId;
        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        if (destination == null) return;

        Matcher matcher = DM_PATTERN.matcher(destination);
        if (matcher.matches()) {
            UUID conversationId = UUID.fromString(matcher.group(1));
            String sessionId = accessor.getSessionId();
            String subscriptionId = accessor.getSubscriptionId();
            String key = sessionId + "_" + subscriptionId;

            Principal principal = accessor.getUser();
            String email = getEmailFromPrincipal(principal);

            if (email != null) {
                userRepository.findByEmail(email).ifPresent(user -> {
                    UUID userId = user.getId();
                    sessionMap.put(key, new UserConversationPair(userId, conversationId));
                    
                    // Redis Set에 추가 (다중 인스턴스 공유)
                    redisTemplate.opsForSet().add(ACTIVE_CONVERSATION_KEY_PREFIX + conversationId, userId.toString());
                    log.info("[WebSocket DM Session] SUBSCRIBE: conversationId={}, userId={}, key={}", conversationId, userId, key);
                });
            }
        }
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String key = sessionId + "_" + subscriptionId;

        removeSession(key);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        sessionMap.keySet().forEach(key -> {
            if (key.startsWith(sessionId + "_")) {
                removeSession(key);
            }
        });
    }

    private void removeSession(String key) {
        UserConversationPair pair = sessionMap.remove(key);
        if (pair != null) {
            UUID userId = pair.userId();
            UUID conversationId = pair.conversationId();
            log.info("[WebSocket DM Session] UNSUBSCRIBE/DISCONNECT: conversationId={}, userId={}, key={}", conversationId, userId, key);

            // 해당 인스턴스에서 해당 사용자가 같은 대화방을 보고 있는 다른 웹소켓 연결이 없는지 검사
            boolean stillSubscribedOnThisInstance = sessionMap.values().stream()
                    .anyMatch(p -> p.userId().equals(userId) && p.conversationId().equals(conversationId));

            if (!stillSubscribedOnThisInstance) {
                // Redis Set에서 제거
                redisTemplate.opsForSet().remove(ACTIVE_CONVERSATION_KEY_PREFIX + conversationId, userId.toString());
            }
        }
    }

    private String getEmailFromPrincipal(Principal principal) {
        if (principal instanceof Authentication) {
            Object principalObj = ((Authentication) principal).getPrincipal();
            if (principalObj instanceof UserDetails) {
                return ((UserDetails) principalObj).getUsername();
            } else if (principalObj instanceof String) {
                return (String) principalObj;
            }
        }
        return principal != null ? principal.getName() : null;
    }

    public record UserConversationPair(UUID userId, UUID conversationId) {}
}
