package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.config.ContentCollectionProperties;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
import com.codeit.mpl.domain.content.entity.ContentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.function.IntFunction;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbContentCollectionService {

    /*
     * TMDB 목록 API가 지원하는 최대 페이지 범위를 제한합니다.
     */
    private static final int TMDB_MAX_PAGE = 500;

    private final TmdbClient tmdbClient;
    private final ContentSyncService contentSyncService;
    private final ContentCollectionProperties properties;

    /*
     * 최초 서비스 DB 구축
     */
    public ContentSyncResult collectInitialMovies() {
        return collectPages(
                "initial-movies",
                ContentType.MOVIE,
                properties.tmdb().initialMaxPages(),
                tmdbClient::discoverMovies
        );
    }

    public ContentSyncResult collectInitialTvSeries() {
        return collectPages(
                "initial-tv-series",
                ContentType.TVSERIES,
                properties.tmdb().initialMaxPages(),
                tmdbClient::discoverTvSeries
        );
    }

    /*
     * 매일 새벽 3시 영화 동기화
     */
    public ContentSyncResult collectDailyMovies() {
        int maxPages =
                properties.tmdb().dailyMaxPages();

        ContentSyncResult result =
                ContentSyncResult.empty();

        result = result.plus(
                collectPages(
                        "popular-movies",
                        ContentType.MOVIE,
                        maxPages,
                        tmdbClient::getPopularMovies
                )
        );

        result = result.plus(
                collectPages(
                        "now-playing-movies",
                        ContentType.MOVIE,
                        maxPages,
                        tmdbClient::getNowPlayingMovies
                )
        );

        result = result.plus(
                collectPages(
                        "upcoming-movies",
                        ContentType.MOVIE,
                        maxPages,
                        tmdbClient::getUpcomingMovies
                )
        );

        return result;
    }

    /*
     * 매일 새벽 3시 TV 시리즈 동기화
     */
    public ContentSyncResult collectDailyTvSeries() {
        int maxPages =
                properties.tmdb().dailyMaxPages();

        ContentSyncResult result =
                ContentSyncResult.empty();

        result = result.plus(
                collectPages(
                        "popular-tv-series",
                        ContentType.TVSERIES,
                        maxPages,
                        tmdbClient::getPopularTvSeries
                )
        );

        result = result.plus(
                collectPages(
                        "on-the-air-tv-series",
                        ContentType.TVSERIES,
                        maxPages,
                        tmdbClient::getOnTheAirTvSeries
                )
        );

        result = result.plus(
                collectPages(
                        "airing-today-tv-series",
                        ContentType.TVSERIES,
                        maxPages,
                        tmdbClient::getAiringTodayTvSeries
                )
        );

        return result;
    }

    private ContentSyncResult collectPages(
            String collectionName,
            ContentType type,
            int configuredMaxPages,
            IntFunction<TmdbSearchResponse> pageLoader
    ) {
        long startedAt = System.currentTimeMillis();

        int maxPages =
                normalizeMaxPages(configuredMaxPages);

        log.info(
                "[TMDB Collection] 목록 수집 시작: collection={}, type={}, maxPages={}",
                collectionName,
                type,
                maxPages
        );

        ContentSyncResult totalResult =
                ContentSyncResult.empty();

        try {
            for (int page = 1; page <= maxPages; page++) {
                TmdbSearchResponse response =
                        pageLoader.apply(page);

                if (response == null
                        || response.results() == null
                        || response.results().isEmpty()) {

                    log.info(
                            "[TMDB Collection] 목록 수집 종료: "
                                    + "collection={}, page={}, reason=empty",
                            collectionName,
                            page
                    );

                    break;
                }

                ContentSyncResult pageResult =
                        contentSyncService.syncTmdbPage(
                                type,
                                response.results()
                        );

                totalResult =
                        totalResult.plus(pageResult);

                log.info(
                        "[TMDB Collection] 페이지 수집 완료: "
                                + "collection={}, page={}, created={}, "
                                + "updated={}, skipped={}",
                        collectionName,
                        page,
                        pageResult.createdCount(),
                        pageResult.updatedCount(),
                        pageResult.skippedCount()
                );

                int totalPages = Math.min(
                        response.total_pages(),
                        TMDB_MAX_PAGE
                );

                if (page >= totalPages) {
                    break;
                }
            }

            log.info(
                    "[TMDB Collection] 목록 수집 완료: "
                            + "collection={}, created={}, updated={}, "
                            + "skipped={}, durationMs={}",
                    collectionName,
                    totalResult.createdCount(),
                    totalResult.updatedCount(),
                    totalResult.skippedCount(),
                    System.currentTimeMillis() - startedAt
            );

            return totalResult;
        } catch (Exception e) {
            log.error(
                    "[TMDB Collection] 목록 수집 실패: "
                            + "collection={}, type={}, durationMs={}",
                    collectionName,
                    type,
                    System.currentTimeMillis() - startedAt,
                    e
            );

            throw e;
        }
    }

    private int normalizeMaxPages(
            int configuredMaxPages
    ) {
        if (configuredMaxPages < 1) {
            return 1;
        }

        return Math.min(
                configuredMaxPages,
                TMDB_MAX_PAGE
        );
    }
}