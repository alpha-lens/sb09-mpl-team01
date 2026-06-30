package com.codeit.mpl.infra.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//ChatRedisListener나 다른 컴포넌트가 Spring Boot의 자동 설정이 완료되기 전에 실행하려고 해서 이거나
//빈 의존성 순서 문제로 앱 실행하는데 오류가 난 것 같아서 일단 JacksonConfig 파일을 만들었습니다.
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
