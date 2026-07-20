package com.codeit.mpl.infra.redis;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * spring.session.store-type: redis 프로퍼티만으로는 HttpSession이 Redis로 연결되지 않아
 * (이 Boot 버전엔 그걸 읽어서 SessionRepository 빈을 만들어주는 auto-configuration이 없음),
 * 각 인스턴스가 세션을 로컬 메모리에 따로 들고 있어 OAuth2 인증 요청(state, PKCE)이
 * 다른 인스턴스로 넘어가면 못 찾는 문제(authorization_request_not_found)가 있었다.
 * auto-configuration에 의존하지 않고 명시적으로 Redis 기반 HttpSession을 활성화한다.
 */
@Configuration
@Profile("!test")
@EnableRedisHttpSession
public class RedisHttpSessionConfig {
}
