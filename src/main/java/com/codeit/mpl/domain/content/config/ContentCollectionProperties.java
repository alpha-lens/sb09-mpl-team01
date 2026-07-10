package com.codeit.mpl.domain.content.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "content.collection")
public record ContentCollectionProperties(
        Tmdb tmdb,
        Sports sports
) {

    public record Tmdb(
            int initialMaxPages,
            int dailyMaxPages
    ) {
    }

    public record Sports(
            List<String> leagueIds
    ) {
        public Sports {
            leagueIds = leagueIds == null
                    ? List.of()
                    : List.copyOf(leagueIds);
        }
    }
}