package com.codeit.mpl.domain.chat.listener;

import com.codeit.mpl.domain.chat.controller.WebHooks.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ChatKafkaConsumerTest {

    @Test
    @DisplayName("Kafka 메세지 수신 시 로그를 남기고 정상적으로 완료된다")
    void consumeChatMessage() {
        ChatKafkaConsumer consumer = new ChatKafkaConsumer();
        ChatMessage message = new ChatMessage("room1", "user1", "hello");

        assertDoesNotThrow(() -> consumer.consumeChatMessage(message));
    }
}
