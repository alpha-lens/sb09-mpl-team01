package com.codeit.mpl.domain.chat.controller;

import com.codeit.mpl.domain.chat.controller.WebHooks.ChatMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;

public class WebsocketController {
  @MessageMapping("/chat/message")
  @SendTo("/topic/public")
  public ChatMessage handleMessage(ChatMessage payload) {
    // 스프링이 클라이언트의 JSON 페이로드를 ChatMessage 객체로 자동 변환하여 주입
    // 가공된 페이로드를 리턴하면 /topic/public을 구독 중인 클라이언트에게 전달됨
    return payload;
  }
}
