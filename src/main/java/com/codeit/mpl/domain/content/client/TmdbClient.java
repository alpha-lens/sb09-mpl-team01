package com.codeit.mpl.domain.content.client;

import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class TmdbClient {

    private final TmdbProperties tmdbProperties;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(tmdbProperties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + tmdbProperties.bearerToken())
                .build();
    }

    public TmdbSearchResponse searchMovies(String keyword) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/movie")
                        .queryParam("query", keyword)
                        .queryParam("language", "ko-KR")
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }

    public TmdbSearchResponse searchTvSeries(String keyword) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/tv")
                        .queryParam("query", keyword)
                        .queryParam("language", "ko-KR")
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }

    public TmdbContentItem getMovieDetail(String externalId) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/{movieId}")
                        .queryParam("language", "ko-KR")
                        .build(externalId)
                )
                .retrieve()
                .body(TmdbContentItem.class);
    }

    public TmdbContentItem getTvSeriesDetail(String externalId) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/tv/{seriesId}")
                        .queryParam("language", "ko-KR")
                        .build(externalId)
                )
                .retrieve()
                .body(TmdbContentItem.class);
    }
}