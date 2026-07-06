package com.codeit.mpl.infra.redis;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@Profile("test")
public class TestRedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return Mockito.mock(RedisConnectionFactory.class);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory());
        return template;
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer() {
        return Mockito.mock(RedisMessageListenerContainer.class);
    }

    @Bean
    public ChannelTopic notificationTopic() {
        return new ChannelTopic("ch-notification");
    }

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("ch-chat");
    }
}
