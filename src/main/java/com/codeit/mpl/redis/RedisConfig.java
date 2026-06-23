package com.codeit.mpl.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
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
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        
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
}
