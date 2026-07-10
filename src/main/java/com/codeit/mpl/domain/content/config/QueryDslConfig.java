package com.codeit.mpl.domain.content.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QueryDslConfig {

    /**
     * QueryDSL JPA 조회에서 공통으로 사용하는 QueryFactory입니다.
     */
    @Bean
    public JPAQueryFactory jpaQueryFactory(
            EntityManager entityManager
    ) {
        return new JPAQueryFactory(entityManager);
    }
}