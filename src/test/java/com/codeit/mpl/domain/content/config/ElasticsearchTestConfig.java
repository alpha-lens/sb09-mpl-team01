package com.codeit.mpl.domain.content.config;

import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ElasticsearchTestConfig {

    @Bean
    @Primary
    public ContentSearchRepository contentSearchRepository() {
        return Mockito.mock(ContentSearchRepository.class);
    }
}
