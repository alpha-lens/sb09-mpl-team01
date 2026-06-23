package com.codeit.mpl.websocket;

import com.codeit.mpl.config.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = resolveToken(accessor);
            
            if (token != null && jwtTokenProvider.validateToken(token)) {
                Authentication authentication = jwtTokenProvider.getAuthentication(token);
                accessor.setUser(authentication);
                log.info("[WebSocket] STOMP 연결 인증 성공. user={}", authentication.getName());
            } else {
                log.warn("[WebSocket] STOMP 연결 인증 실패. 유효하지 않은 토큰입니다.");
                throw new IllegalArgumentException("유효하지 않은 JWT 토큰입니다.");
            }
        }
        
        return message;
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        // 1. STOMP Header 'Authorization'에서 Bearer 토큰 추출
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        
        // 2. Query 파라미터나 다른 경로로 토큰을 보내는 경우에 대한 대응
        // 클라이언트 라이브러리에 따라 다를 수 있으므로 커스텀 헤더 'token'도 체크
        String customToken = accessor.getFirstNativeHeader("token");
        if (customToken != null) {
            return customToken;
        }
        
        return null;
    }
}
