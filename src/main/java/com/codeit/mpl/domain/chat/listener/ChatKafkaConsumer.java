package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.controller.WebHooks.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ChatKafkaConsumer {

    /**
     * Consumes chat messages from the chat topic.
     *
     * @param message the chat message received from Kafka
     */
    @KafkaListener(topics = "chat-topic", groupId = "mpl-group")
    public void consumeChatMessage(ChatMessage message) {
        log.info("[Kafka] 채팅 메시지 수신 (영속화용): room={}, sender={}, message={}",
                message.getRoomId(), message.getSender(), message.getMessage());
        
        // TODO: 여기서 DB(RDB/NoSQL/Elasticsearch)에 대량 쓰기 비동기 영속화 작업 수행
    }
}
