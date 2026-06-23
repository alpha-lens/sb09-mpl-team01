package com.codeit.mpl.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketChannelInterceptor webSocketChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메시지 구독 요청 prefix (Server -> Client 브로드캐스트)
        // /topic: 1:N 공용 브로드캐스트, /queue: 1:1 전용
        registry.enableSimpleBroker("/topic", "/queue");
        
        // 메시지 발행 요청 prefix (Client -> Server 처리 대상)
        registry.setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 웹소켓 핸드셰이크 커넥션 엔드포인트 설정
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*")
                .withSockJS();
                
        // SockJS 미지원 브라우저/클라이언트를 위한 일반 WebSocket 연결도 제공
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // JWT 검증 인터셉터를 인바운드 채널에 설정
        registration.interceptors(webSocketChannelInterceptor);
    }
}
