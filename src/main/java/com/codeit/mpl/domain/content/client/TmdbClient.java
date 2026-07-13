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
                .defaultHeader(
                        "Authorization",
                        "Bearer " + tmdbProperties.bearerToken()
                )
                .build();
    }

    /*
     * 관리자 수동 검색용
     */
    public TmdbSearchResponse searchMovies(String keyword) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/movie")
                        .queryParam("query", keyword)
                        .queryParam("language", "ko-KR")
                        .queryParam("include_adult", false)
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
                        .queryParam("include_adult", false)
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }

    /*
     * 서비스 최초 구축 시 대량 수집용
     */
    public TmdbSearchResponse discoverMovies(int page) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/discover/movie")
                        .queryParam("language", "ko-KR")
                        .queryParam("region", "KR")
                        .queryParam("include_adult", false)
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("page", page)
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }

    public TmdbSearchResponse discoverTvSeries(int page) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/discover/tv")
                        .queryParam("language", "ko-KR")
                        .queryParam("include_adult", false)
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("page", page)
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }

    /*
     * 매일 새벽 3시 영화 동기화
     */
    public TmdbSearchResponse getPopularMovies(int page) {
        return getMovieList("/movie/popular", page);
    }

    public TmdbSearchResponse getNowPlayingMovies(int page) {
        return getMovieList("/movie/now_playing", page);
    }

    public TmdbSearchResponse getUpcomingMovies(int page) {
        return getMovieList("/movie/upcoming", page);
    }

    /*
     * 매일 새벽 3시 TV 시리즈 동기화
     */
    public TmdbSearchResponse getPopularTvSeries(int page) {
        return getTvList("/tv/popular", page);
    }

    public TmdbSearchResponse getOnTheAirTvSeries(int page) {
        return getTvList("/tv/on_the_air", page);
    }

    public TmdbSearchResponse getAiringTodayTvSeries(int page) {
        return getTvList("/tv/airing_today", page);
    }

    /*
     * 관리자 수동 Import 상세 조회
     */
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

    private TmdbSearchResponse getMovieList(
            String path,
            int page
    ) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("language", "ko-KR")
                        .queryParam("region", "KR")
                        .queryParam("page", page)
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }

    private TmdbSearchResponse getTvList(
            String path,
            int page
    ) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        .queryParam("language", "ko-KR")
                        .queryParam("page", page)
                        .build()
                )
                .retrieve()
                .body(TmdbSearchResponse.class);
    }
}