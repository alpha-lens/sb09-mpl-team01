package com.codeit.mpl.domain.content.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tmdb")
public record TmdbProperties(
        String baseUrl,
        String imageBaseUrl,
        String bearerToken
) {
}