package com.codeit.mpl.domain.content.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sportsdb")
public record SportsDbProperties(
        String baseUrl,
        String apiKey
) {
}