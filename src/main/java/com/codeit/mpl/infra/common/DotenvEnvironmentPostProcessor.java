package com.codeit.mpl.infra.common;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.HashMap;
import java.util.Map;

// .env파일을 읽기 위해 만든 클래스. 로컬 실행(Run 버튼)에서 .env를 자동 로드하는 유일한 수단이라
// docker-compose env_file: 방식으로 완전히 전환할지는 별도 결정 필요.
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /**
     * ConfigDataEnvironmentPostProcessor(활성 프로필 결정, application-{profile}.yaml 로딩)가
     * HIGHEST_PRECEDENCE+10으로 실행되므로, 그보다 먼저 실행되도록 HIGHEST_PRECEDENCE를 반환한다.
     * 순서 지정 없이 기본값(가장 나중)으로 실행되면 .env를 읽어 넣는 시점엔 이미 활성 프로필이
     * 확정된 뒤라 SPRING_PROFILES_ACTIVE 같은 값이 프로필 결정에 전혀 반영되지 못한다(실제로
     * .env에 prod로 지정해도 dev로 폴백되는 것으로 확인됨).
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * MapPropertySource가 아니라 SystemEnvironmentPropertySource를 쓴다. MapPropertySource는
     * .env의 원본 키(예: SPRING_KAFKA_BOOTSTRAP_SERVERS)를 그대로 저장해서 Spring의 relaxed
     * binding(대문자_언더스코어 ↔ dot.표기 자동 매칭)이 적용되지 않아, yaml에 리터럴로 박힌
     * spring.kafka.bootstrap-servers 같은 값을 override하지 못했다. SystemEnvironmentPropertySource는
     * 실제 OS 환경변수처럼 relaxed binding을 지원한다.
     */
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        Map<String, Object> props = new HashMap<>();
        dotenv.entries().forEach(entry -> props.put(entry.getKey(), entry.getValue()));
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("dotenv", props));
    }
}
