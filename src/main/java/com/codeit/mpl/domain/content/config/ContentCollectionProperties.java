package com.codeit.mpl.domain.content.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.collection")
public record ContentCollectionProperties(
        Tmdb tmdb
) {

    /**
     * TMDB 콘텐츠 수집 설정입니다.
     */
    public record Tmdb(
            int initialMaxPages,
            int dailyMaxPages
    ) {
    }
}
