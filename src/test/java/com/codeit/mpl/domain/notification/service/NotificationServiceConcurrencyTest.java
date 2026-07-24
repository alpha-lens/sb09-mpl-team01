package com.codeit.mpl.domain.notification.service;

import com.codeit.mpl.domain.conversation.entity.Conversation;
import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import com.codeit.mpl.domain.conversation.repository.ConversationRepository;
import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.data.redis.repositories.enabled=false",
    "spring.data.elasticsearch.repositories.enabled=false"
})
class NotificationServiceConcurrencyTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
            RedisTemplate<String, Object> template = mock(RedisTemplate.class);
            when(template.getConnectionFactory()).thenReturn(connectionFactory);
            return template;
        }

        @Bean
        public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
            StringRedisTemplate template = mock(StringRedisTemplate.class);
            when(template.getConnectionFactory()).thenReturn(connectionFactory);
            return template;
        }

        @Bean
        public RedisMessageListenerContainer redisMessageListenerContainer() {
            return mock(RedisMessageListenerContainer.class);
        }

        @Bean
        public org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate() {
            return mock(org.springframework.kafka.core.KafkaTemplate.class);
        }
    }

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private DirectMessageRepository directMessageRepository;

    @Test
    @DisplayName("동시 요청 시 중복 unread DM 알림이 발생하지 않고 압축되어야 한다")
    void saveNotification_concurrency_compress() throws InterruptedException {
        // given
        User sender = User.builder()
            .email("sender@test.com")
            .password("password")
            .name("Sender")
            .build();
        User receiver = User.builder()
            .email("receiver@test.com")
            .password("password")
            .name("Receiver")
            .build();
        userRepository.saveAll(List.of(sender, receiver));

        Conversation conversation = Conversation.create(sender, receiver);
        conversationRepository.save(conversation);

        DirectMessage dm1 = DirectMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .receiver(receiver)
            .content("Hello 1")
            .isRead(false)
            .build();
        DirectMessage dm2 = DirectMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .receiver(receiver)
            .content("Hello 2")
            .isRead(false)
            .build();
        directMessageRepository.saveAll(List.of(dm1, dm2));

        NotificationEvent event = new NotificationEvent(
            receiver,
            sender,
            NotificationLevel.INFO,
            "새 메시지",
            "Hello 2",
            NotificationType.DM,
            conversation.getId()
        );

        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    latch.await();
                    notificationService.saveNotification(event);
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        boolean allFinished = doneLatch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);

        // then
        assertThat(allFinished).as("모든 스레드가 제한 시간 내에 완료되어야 합니다").isTrue();
        
        List<Exception> unexpectedExceptions = exceptions.stream()
            .filter(e -> !(e instanceof org.springframework.dao.DataIntegrityViolationException))
            .toList();
        assertThat(unexpectedExceptions).as("스레드 실행 중 예상치 못한 예외가 발생하지 않아야 합니다").isEmpty();
        List<Notification> notifications = notificationRepository.findAll();
        long unreadDmCount = notifications.stream()
            .filter(n -> n.getType() == NotificationType.DM && !n.isRead())
            .count();

        assertThat(unreadDmCount).isEqualTo(1L);
    }
}
