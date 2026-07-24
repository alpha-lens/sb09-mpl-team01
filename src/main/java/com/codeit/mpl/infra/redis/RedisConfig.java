package com.codeit.mpl.infra.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@Profile("!test")
public class RedisConfig {

    /**
     * Creates a RedisTemplate configured with string serialization for keys and JSON serialization for values.
     *
     * @return a configured RedisTemplate instance
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // key는 StringSerializer, value는 JSONSerializer 설정
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.json());
        template.setHashValueSerializer(RedisSerializer.json());
        
        return template;
    }

    /**
     * Creates a Redis message listener container bean for handling Pub/Sub messages.
     *
     * @return a configured RedisMessageListenerContainer
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * Creates a Redis Pub/Sub topic for notification messages.
     *
     * @return a channel topic for notifications
     */
    @Bean
    public ChannelTopic notificationTopic() {
        return new ChannelTopic("ch-notification");
    }

    /**
     * Creates a Redis Pub/Sub topic for chat messages.
     *
     * @return a ChannelTopic configured for the 'ch-chat' channel
     */
    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("ch-chat");
    }

    /**
     * Creates a Redis Pub/Sub topic for watching session events.
     *
     * @return a ChannelTopic configured for the 'ch-watching-session' channel
     */
    @Bean
    public ChannelTopic watchingSessionTopic() {
        return new ChannelTopic("ch-watching-session");
    }
}


