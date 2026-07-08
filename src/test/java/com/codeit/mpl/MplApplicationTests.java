package com.codeit.mpl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest
class MplApplicationTests {

  @MockitoBean
  RedisTemplate<String, Object> redisTemplate;

  @Test
  void contextLoads() {
  }
}