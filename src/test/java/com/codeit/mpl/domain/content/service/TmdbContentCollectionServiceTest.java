package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.config.ContentCollectionProperties;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
import com.codeit.mpl.domain.content.entity.ContentType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TmdbContentCollectionService 단위 테스트")
class TmdbContentCollectionServiceTest {

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private ContentSyncService contentSyncService;

    /*
     * properties.tmdb().initialMaxPages()와 같이
     * 중첩된 설정 객체의 메서드를 호출하므로 Deep Stub을 사용합니다.
     */
    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ContentCollectionProperties properties;

    private TmdbContentCollectionService tmdbContentCollectionService;

    @BeforeEach
    void setUp() {
        tmdbContentCollectionService =
                new TmdbContentCollectionService(
                        tmdbClient,
                        contentSyncService,
                        properties
                );
    }

    @Nested
    @DisplayName("최초 영화 수집")
    class CollectInitialMoviesTest {

        @Test
        @DisplayName("여러 페이지의 영화 수집 결과를 합산한다")
        void collectInitialMovies_success() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(2);

            TmdbContentItem firstItem =
                    mock(TmdbContentItem.class);

            TmdbContentItem secondItem =
                    mock(TmdbContentItem.class);

            TmdbSearchResponse firstResponse =
                    createResponse(
                            List.of(firstItem),
                            2
                    );

            TmdbSearchResponse secondResponse =
                    createResponse(
                            List.of(secondItem),
                            2
                    );

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(firstResponse);

            when(tmdbClient.discoverMovies(2))
                    .thenReturn(secondResponse);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    firstResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            2,
                            1,
                            0
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    secondResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            2,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    5,
                                    3,
                                    1
                            )
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient)
                    .discoverMovies(2);

            verify(contentSyncService)
                    .syncTmdbPage(
                            ContentType.MOVIE,
                            firstResponse.results()
                    );

            verify(contentSyncService)
                    .syncTmdbPage(
                            ContentType.MOVIE,
                            secondResponse.results()
                    );
        }

        @Test
        @DisplayName("TMDB 전체 페이지 수에 도달하면 수집을 종료한다")
        void collectInitialMovies_stopsAtTotalPages() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(10);

            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(item),
                            1
                    );

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(response);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    response.results()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient, never())
                    .discoverMovies(2);
        }

        @Test
        @DisplayName("최대 페이지 설정이 0이면 최소 1페이지를 수집한다")
        void collectInitialMovies_normalizesZeroToOne() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(0);

            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(item),
                            100
                    );

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(response);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    response.results()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient, never())
                    .discoverMovies(2);
        }

        @Test
        @DisplayName("최대 페이지 설정이 음수이면 최소 1페이지를 수집한다")
        void collectInitialMovies_normalizesNegativeToOne() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(-10);

            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(item),
                            100
                    );

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(response);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    response.results()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result.createdCount())
                    .isEqualTo(1);

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient, never())
                    .discoverMovies(2);
        }

        @Test
        @DisplayName("TMDB 응답이 null이면 빈 결과를 반환하고 수집을 종료한다")
        void collectInitialMovies_responseIsNull() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(3);

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(null);

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            ContentSyncResult.empty()
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient, never())
                    .discoverMovies(2);

            verify(contentSyncService, never())
                    .syncTmdbPage(
                            eq(ContentType.MOVIE),
                            anyList()
                    );
        }

        @Test
        @DisplayName("TMDB 결과 목록이 null이면 빈 결과를 반환하고 수집을 종료한다")
        void collectInitialMovies_resultsAreNull() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(3);

            TmdbSearchResponse response =
                    mock(TmdbSearchResponse.class);

            when(response.results())
                    .thenReturn(null);

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(response);

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            ContentSyncResult.empty()
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient, never())
                    .discoverMovies(2);

            verify(contentSyncService, never())
                    .syncTmdbPage(
                            eq(ContentType.MOVIE),
                            anyList()
                    );
        }

        @Test
        @DisplayName("TMDB 결과 목록이 비어 있으면 빈 결과를 반환하고 수집을 종료한다")
        void collectInitialMovies_resultsAreEmpty() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(3);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(),
                            3
                    );

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(response);

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            ContentSyncResult.empty()
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient, never())
                    .discoverMovies(2);

            verify(contentSyncService, never())
                    .syncTmdbPage(
                            eq(ContentType.MOVIE),
                            anyList()
                    );
        }

        @Test
        @DisplayName("두 번째 페이지가 비어 있으면 첫 번째 페이지 결과만 반환한다")
        void collectInitialMovies_secondPageIsEmpty() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(5);

            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            TmdbSearchResponse firstResponse =
                    createResponse(
                            List.of(item),
                            5
                    );

            TmdbSearchResponse secondResponse =
                    createResponse(
                            List.of(),
                            5
                    );

            when(tmdbClient.discoverMovies(1))
                    .thenReturn(firstResponse);

            when(tmdbClient.discoverMovies(2))
                    .thenReturn(secondResponse);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    firstResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            2,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    3,
                                    2,
                                    1
                            )
                    );

            verify(tmdbClient)
                    .discoverMovies(1);

            verify(tmdbClient)
                    .discoverMovies(2);

            verify(tmdbClient, never())
                    .discoverMovies(3);

            verify(contentSyncService, times(1))
                    .syncTmdbPage(
                            ContentType.MOVIE,
                            firstResponse.results()
                    );
        }
    }

    @Nested
    @DisplayName("최초 TV 시리즈 수집")
    class CollectInitialTvSeriesTest {

        @Test
        @DisplayName("TV 시리즈를 TVSERIES 타입으로 동기화한다")
        void collectInitialTvSeries_success() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(1);

            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(item),
                            1
                    );

            when(tmdbClient.discoverTvSeries(1))
                    .thenReturn(response);

            when(contentSyncService.syncTmdbPage(
                    ContentType.TVSERIES,
                    response.results()
            )).thenReturn(
                    new ContentSyncResult(
                            4,
                            2,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialTvSeries();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    4,
                                    2,
                                    1
                            )
                    );

            verify(tmdbClient)
                    .discoverTvSeries(1);

            verify(contentSyncService)
                    .syncTmdbPage(
                            ContentType.TVSERIES,
                            response.results()
                    );
        }
    }

    @Nested
    @DisplayName("일일 영화 수집")
    class CollectDailyMoviesTest {

        @Test
        @DisplayName("인기, 현재 상영, 개봉 예정 영화 수집 결과를 합산한다")
        void collectDailyMovies_combinesAllResults() {
            // given
            when(properties.tmdb().dailyMaxPages())
                    .thenReturn(1);

            TmdbSearchResponse popularResponse =
                    createResponseWithSingleItem();

            TmdbSearchResponse nowPlayingResponse =
                    createResponseWithSingleItem();

            TmdbSearchResponse upcomingResponse =
                    createResponseWithSingleItem();

            when(tmdbClient.getPopularMovies(1))
                    .thenReturn(popularResponse);

            when(tmdbClient.getNowPlayingMovies(1))
                    .thenReturn(nowPlayingResponse);

            when(tmdbClient.getUpcomingMovies(1))
                    .thenReturn(upcomingResponse);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    popularResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            2,
                            3
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    nowPlayingResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            4,
                            5,
                            6
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    upcomingResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            7,
                            8,
                            9
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectDailyMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    12,
                                    15,
                                    18
                            )
                    );

            verify(tmdbClient)
                    .getPopularMovies(1);

            verify(tmdbClient)
                    .getNowPlayingMovies(1);

            verify(tmdbClient)
                    .getUpcomingMovies(1);
        }

        @Test
        @DisplayName("인기 영화 결과가 비어 있어도 나머지 영화 목록을 계속 수집한다")
        void collectDailyMovies_continuesAfterEmptyCollection() {
            // given
            when(properties.tmdb().dailyMaxPages())
                    .thenReturn(1);

            TmdbSearchResponse emptyPopularResponse =
                    createResponse(
                            List.of(),
                            1
                    );

            TmdbSearchResponse nowPlayingResponse =
                    createResponseWithSingleItem();

            TmdbSearchResponse upcomingResponse =
                    createResponseWithSingleItem();

            when(tmdbClient.getPopularMovies(1))
                    .thenReturn(emptyPopularResponse);

            when(tmdbClient.getNowPlayingMovies(1))
                    .thenReturn(nowPlayingResponse);

            when(tmdbClient.getUpcomingMovies(1))
                    .thenReturn(upcomingResponse);

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    nowPlayingResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            2,
                            1,
                            0
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.MOVIE,
                    upcomingResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            2,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectDailyMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    5,
                                    3,
                                    1
                            )
                    );

            verify(contentSyncService, never())
                    .syncTmdbPage(
                            ContentType.MOVIE,
                            emptyPopularResponse.results()
                    );

            verify(contentSyncService)
                    .syncTmdbPage(
                            ContentType.MOVIE,
                            nowPlayingResponse.results()
                    );

            verify(contentSyncService)
                    .syncTmdbPage(
                            ContentType.MOVIE,
                            upcomingResponse.results()
                    );
        }
    }

    @Nested
    @DisplayName("일일 TV 시리즈 수집")
    class CollectDailyTvSeriesTest {

        @Test
        @DisplayName("인기, 방영 중, 오늘 방영 TV 시리즈 수집 결과를 합산한다")
        void collectDailyTvSeries_combinesAllResults() {
            // given
            when(properties.tmdb().dailyMaxPages())
                    .thenReturn(1);

            TmdbSearchResponse popularResponse =
                    createResponseWithSingleItem();

            TmdbSearchResponse onTheAirResponse =
                    createResponseWithSingleItem();

            TmdbSearchResponse airingTodayResponse =
                    createResponseWithSingleItem();

            when(tmdbClient.getPopularTvSeries(1))
                    .thenReturn(popularResponse);

            when(tmdbClient.getOnTheAirTvSeries(1))
                    .thenReturn(onTheAirResponse);

            when(tmdbClient.getAiringTodayTvSeries(1))
                    .thenReturn(airingTodayResponse);

            when(contentSyncService.syncTmdbPage(
                    ContentType.TVSERIES,
                    popularResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            1
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.TVSERIES,
                    onTheAirResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            2,
                            3,
                            0
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.TVSERIES,
                    airingTodayResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            4,
                            1,
                            2
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectDailyTvSeries();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    7,
                                    4,
                                    3
                            )
                    );

            verify(tmdbClient)
                    .getPopularTvSeries(1);

            verify(tmdbClient)
                    .getOnTheAirTvSeries(1);

            verify(tmdbClient)
                    .getAiringTodayTvSeries(1);
        }

        @Test
        @DisplayName("일부 TV 목록 응답이 null이어도 다른 목록 수집은 계속한다")
        void collectDailyTvSeries_continuesAfterNullCollection() {
            // given
            when(properties.tmdb().dailyMaxPages())
                    .thenReturn(1);

            TmdbSearchResponse onTheAirResponse =
                    createResponseWithSingleItem();

            TmdbSearchResponse airingTodayResponse =
                    createResponseWithSingleItem();

            when(tmdbClient.getPopularTvSeries(1))
                    .thenReturn(null);

            when(tmdbClient.getOnTheAirTvSeries(1))
                    .thenReturn(onTheAirResponse);

            when(tmdbClient.getAiringTodayTvSeries(1))
                    .thenReturn(airingTodayResponse);

            when(contentSyncService.syncTmdbPage(
                    ContentType.TVSERIES,
                    onTheAirResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            2,
                            1,
                            0
                    )
            );

            when(contentSyncService.syncTmdbPage(
                    ContentType.TVSERIES,
                    airingTodayResponse.results()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            2,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectDailyTvSeries();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    5,
                                    3,
                                    1
                            )
                    );

            verify(tmdbClient)
                    .getPopularTvSeries(1);

            verify(tmdbClient)
                    .getOnTheAirTvSeries(1);

            verify(tmdbClient)
                    .getAiringTodayTvSeries(1);
        }
    }

    @Nested
    @DisplayName("TMDB 최대 페이지 제한")
    class MaxPageLimitTest {

        @Test
        @DisplayName("설정값이 500보다 크면 최대 500페이지만 요청한다")
        void collectInitialMovies_limitsPagesToFiveHundred() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(700);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(
                                    mock(TmdbContentItem.class)
                            ),
                            700
                    );

            when(tmdbClient.discoverMovies(anyInt()))
                    .thenReturn(response);

            when(contentSyncService.syncTmdbPage(
                    eq(ContentType.MOVIE),
                    anyList()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    500,
                                    0,
                                    0
                            )
                    );

            verify(tmdbClient, times(500))
                    .discoverMovies(anyInt());

            verify(tmdbClient, never())
                    .discoverMovies(501);
        }

        @Test
        @DisplayName("TMDB 응답의 전체 페이지가 500보다 커도 500페이지에서 종료한다")
        void collectInitialMovies_limitsResponseTotalPagesToFiveHundred() {
            // given
            when(properties.tmdb().initialMaxPages())
                    .thenReturn(500);

            TmdbSearchResponse response =
                    createResponse(
                            List.of(
                                    mock(TmdbContentItem.class)
                            ),
                            1000
                    );

            when(tmdbClient.discoverMovies(anyInt()))
                    .thenReturn(response);

            when(contentSyncService.syncTmdbPage(
                    eq(ContentType.MOVIE),
                    anyList()
            )).thenReturn(
                    new ContentSyncResult(
                            0,
                            1,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    tmdbContentCollectionService
                            .collectInitialMovies();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    0,
                                    500,
                                    0
                            )
                    );

            verify(tmdbClient, times(500))
                    .discoverMovies(anyInt());

            verify(tmdbClient, never())
                    .discoverMovies(501);
        }
    }

    /**
     * 한 개의 TMDB 콘텐츠가 포함된 1페이지 응답을 생성합니다.
     */
    private TmdbSearchResponse createResponseWithSingleItem() {
        return createResponse(
                List.of(
                        mock(TmdbContentItem.class)
                ),
                1
        );
    }

    /**
     * 테스트용 TMDB 검색 응답을 생성합니다.
     *
     * 빈 결과나 null 결과인 경우에는 total_pages()가 호출되지 않을 수 있으므로
     * 불필요한 스텁 예외를 방지하기 위해 lenient 스텁을 사용합니다.
     */
    private TmdbSearchResponse createResponse(
            List<TmdbContentItem> results,
            int totalPages
    ) {
        TmdbSearchResponse response =
                mock(TmdbSearchResponse.class);

        when(response.results())
                .thenReturn(results);

        lenient()
                .when(response.total_pages())
                .thenReturn(totalPages);

        return response;
    }
}

