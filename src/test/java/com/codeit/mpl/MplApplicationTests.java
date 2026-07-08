package com.codeit.mpl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;

import static org.mockito.Mockito.mock;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.data.redis.repositories.enabled=false")
class MplApplicationTests {

  @TestConfiguration
  static class TestConfig {
      @Bean
      public RedisConnectionFactory redisConnectionFactory() {
          return mock(RedisConnectionFactory.class);
      }

      @Bean
      public RedisTemplate<String, Object> redisTemplate() {
          return mock(RedisTemplate.class);
      }

      @Bean
      public StringRedisTemplate stringRedisTemplate() {
          return mock(StringRedisTemplate.class);
      }

      @Bean
      public RedisMessageListenerContainer redisMessageListenerContainer() {
          return mock(RedisMessageListenerContainer.class);
      }
  }

  @Test
  void contextLoads() {
  }

}
