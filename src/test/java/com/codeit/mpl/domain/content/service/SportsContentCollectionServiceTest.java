package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueResponse;
import java.time.Year;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SportsContentCollectionService 단위 테스트")
class SportsContentCollectionServiceTest {

    private static final ZoneId SEOUL_ZONE =
            ZoneId.of("Asia/Seoul");

    @Mock
    private SportsDbClient sportsDbClient;

    @Mock
    private ContentSyncService contentSyncService;

    private SportsContentCollectionService collectionService;

    @BeforeEach
    void setUp() {
        collectionService =
                new SportsContentCollectionService(
                        sportsDbClient,
                        contentSyncService
                );
    }

    @Nested
    @DisplayName("초기 시즌 경기 수집")
    class CollectInitialSeasonEventsTest {

        @Test
        @DisplayName("전체 리그 응답이 null이면 빈 결과를 반환한다")
        void collectInitialSeasonEvents_returnsEmptyWhenResponseIsNull() {
            // given
            when(sportsDbClient.getAllLeagues())
                    .thenReturn(null);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(sportsDbClient)
                    .getAllLeagues();

            verify(sportsDbClient, never())
                    .getSeasonEvents(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString()
                    );

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("전체 리그 목록이 null이면 빈 결과를 반환한다")
        void collectInitialSeasonEvents_returnsEmptyWhenLeaguesAreNull() {
            // given
            SportsDbLeagueResponse response =
                    new SportsDbLeagueResponse(
                            null
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(response);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("전체 리그 목록이 비어 있으면 빈 결과를 반환한다")
        void collectInitialSeasonEvents_returnsEmptyWhenLeaguesAreEmpty() {
            // given
            SportsDbLeagueResponse response =
                    createLeagueResponse(
                            List.of()
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(response);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("유효하지 않은 리그만 있으면 빈 결과를 반환한다")
        void collectInitialSeasonEvents_filtersInvalidLeagues() {
            // given
            SportsDbLeagueItem missingId =
                    createLeague(
                            null,
                            "리그",
                            "Soccer"
                    );

            SportsDbLeagueItem missingName =
                    createLeague(
                            "2",
                            " ",
                            "Basketball"
                    );

            SportsDbLeagueItem missingSport =
                    createLeague(
                            "3",
                            "리그",
                            null
                    );

            SportsDbLeagueResponse response =
                    createLeagueResponse(
                            Arrays.asList(
                                    null,
                                    missingId,
                                    missingName,
                                    missingSport
                            )
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(response);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(sportsDbClient)
                    .getAllLeagues();

            verify(sportsDbClient, never())
                    .getSeasonEvents(
                            org.mockito.ArgumentMatchers.anyString(),
                            org.mockito.ArgumentMatchers.anyString()
                    );

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("최신 시즌에 경기 데이터가 있으면 해당 시즌만 동기화한다")
        void collectInitialSeasonEvents_collectsFirstAvailableSeason() {
            // given
            int currentYear =
                    Year.now(SEOUL_ZONE)
                            .getValue();

            String newestSeason =
                    currentYear
                            + "-"
                            + (currentYear + 1);

            String previousSeason =
                    (currentYear - 1)
                            + "-"
                            + currentYear;

            SportsDbLeagueItem league =
                    createLeague(
                            "100",
                            "Premier League",
                            "Soccer"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(league)
                    );

            SportsDbEventItem event =
                    createEvent("event-1");

            SportsDbEventResponse emptyResponse =
                    createEventResponse(
                            List.of()
                    );

            SportsDbEventResponse eventResponse =
                    createEventResponse(
                            List.of(event)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getSeasonEvents(
                    "100",
                    newestSeason
            )).thenReturn(emptyResponse);

            when(sportsDbClient.getSeasonEvents(
                    "100",
                    previousSeason
            )).thenReturn(eventResponse);

            when(contentSyncService.syncSportsEvents(
                    eventResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            2,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    3,
                                    2,
                                    1
                            )
                    );

            verify(sportsDbClient)
                    .getSeasonEvents(
                            "100",
                            newestSeason
                    );

            verify(sportsDbClient)
                    .getSeasonEvents(
                            "100",
                            previousSeason
                    );

            verify(contentSyncService)
                    .syncSportsEvents(
                            eventResponse.events()
                    );

            verify(sportsDbClient, times(2))
                    .getSeasonEvents(
                            org.mockito.ArgumentMatchers.eq("100"),
                            org.mockito.ArgumentMatchers.anyString()
                    );
        }

        @Test
        @DisplayName("모든 시즌 후보에 경기 데이터가 없으면 리그를 건너뛴다")
        void collectInitialSeasonEvents_skipsLeagueWhenAllSeasonsAreEmpty() {
            // given
            SportsDbLeagueItem league =
                    createLeague(
                            "200",
                            "Empty League",
                            "Soccer"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(league)
                    );

            SportsDbEventResponse emptyResponse =
                    createEventResponse(
                            List.of()
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getSeasonEvents(
                    org.mockito.ArgumentMatchers.eq("200"),
                    org.mockito.ArgumentMatchers.anyString()
            )).thenReturn(emptyResponse);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(sportsDbClient, times(4))
                    .getSeasonEvents(
                            org.mockito.ArgumentMatchers.eq("200"),
                            org.mockito.ArgumentMatchers.anyString()
                    );

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("여러 리그의 초기 수집 결과를 합산한다")
        void collectInitialSeasonEvents_combinesResults() {
            // given
            int currentYear =
                    Year.now(SEOUL_ZONE)
                            .getValue();

            String newestSeason =
                    currentYear
                            + "-"
                            + (currentYear + 1);

            SportsDbLeagueItem firstLeague =
                    createLeague(
                            "300",
                            "첫 번째 리그",
                            "Soccer"
                    );

            SportsDbLeagueItem secondLeague =
                    createLeague(
                            "400",
                            "두 번째 리그",
                            "Basketball"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(
                                    firstLeague,
                                    secondLeague
                            )
                    );

            SportsDbEventItem firstEvent =
                    createEvent("event-1");

            SportsDbEventItem secondEvent =
                    createEvent("event-2");

            SportsDbEventResponse firstResponse =
                    createEventResponse(
                            List.of(firstEvent)
                    );

            SportsDbEventResponse secondResponse =
                    createEventResponse(
                            List.of(secondEvent)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getSeasonEvents(
                    "300",
                    newestSeason
            )).thenReturn(firstResponse);

            when(sportsDbClient.getSeasonEvents(
                    "400",
                    newestSeason
            )).thenReturn(secondResponse);

            when(contentSyncService.syncSportsEvents(
                    firstResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            2,
                            1,
                            0
                    )
            );

            when(contentSyncService.syncSportsEvents(
                    secondResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            4,
                            1
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    5,
                                    5,
                                    1
                            )
                    );
        }

        @Test
        @DisplayName("동일한 리그 ID가 중복되면 첫 번째 리그만 수집한다")
        void collectInitialSeasonEvents_deduplicatesLeaguesById() {
            // given
            int currentYear =
                    Year.now(SEOUL_ZONE)
                            .getValue();

            String newestSeason =
                    currentYear
                            + "-"
                            + (currentYear + 1);

            SportsDbLeagueItem firstLeague =
                    createLeague(
                            "500",
                            "첫 번째 리그명",
                            "Soccer"
                    );

            SportsDbLeagueItem duplicatedLeague =
                    createLeague(
                            "500",
                            "중복 리그명",
                            "Basketball"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(
                                    firstLeague,
                                    duplicatedLeague
                            )
                    );

            SportsDbEventItem event =
                    createEvent("event-1");

            SportsDbEventResponse eventResponse =
                    createEventResponse(
                            List.of(event)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getSeasonEvents(
                    "500",
                    newestSeason
            )).thenReturn(eventResponse);

            when(contentSyncService.syncSportsEvents(
                    eventResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectInitialSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            verify(sportsDbClient, times(1))
                    .getSeasonEvents(
                            "500",
                            newestSeason
                    );
        }
    }

    @Nested
    @DisplayName("일일 예정 경기 수집")
    class CollectCurrentSeasonEventsTest {

        @Test
        @DisplayName("리그가 없으면 빈 결과를 반환한다")
        void collectCurrentSeasonEvents_returnsEmptyWhenNoLeaguesExist() {
            // given
            when(sportsDbClient.getAllLeagues())
                    .thenReturn(null);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectCurrentSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(sportsDbClient, never())
                    .getNextEventsByLeague(
                            org.mockito.ArgumentMatchers.anyString()
                    );

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("리그별 예정 경기 수집 결과를 합산한다")
        void collectCurrentSeasonEvents_combinesLeagueResults() {
            // given
            SportsDbLeagueItem firstLeague =
                    createLeague(
                            "600",
                            "첫 번째 리그",
                            "Soccer"
                    );

            SportsDbLeagueItem secondLeague =
                    createLeague(
                            "700",
                            "두 번째 리그",
                            "Baseball"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(
                                    firstLeague,
                                    secondLeague
                            )
                    );

            SportsDbEventItem firstEvent =
                    createEvent("event-1");

            SportsDbEventItem secondEvent =
                    createEvent("event-2");

            SportsDbEventResponse firstResponse =
                    createEventResponse(
                            List.of(firstEvent)
                    );

            SportsDbEventResponse secondResponse =
                    createEventResponse(
                            List.of(secondEvent)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getNextEventsByLeague(
                    "600"
            )).thenReturn(firstResponse);

            when(sportsDbClient.getNextEventsByLeague(
                    "700"
            )).thenReturn(secondResponse);

            when(contentSyncService.syncSportsEvents(
                    firstResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            2,
                            0
                    )
            );

            when(contentSyncService.syncSportsEvents(
                    secondResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            3,
                            1,
                            2
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectCurrentSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    4,
                                    3,
                                    2
                            )
                    );

            verify(contentSyncService)
                    .syncSportsEvents(
                            firstResponse.events()
                    );

            verify(contentSyncService)
                    .syncSportsEvents(
                            secondResponse.events()
                    );
        }

        @Test
        @DisplayName("예정 경기 응답이 null인 리그는 건너뛴다")
        void collectCurrentSeasonEvents_skipsNullEventResponse() {
            // given
            SportsDbLeagueItem firstLeague =
                    createLeague(
                            "800",
                            "빈 리그",
                            "Soccer"
                    );

            SportsDbLeagueItem secondLeague =
                    createLeague(
                            "900",
                            "경기 있는 리그",
                            "Basketball"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(
                                    firstLeague,
                                    secondLeague
                            )
                    );

            SportsDbEventItem event =
                    createEvent("event-1");

            SportsDbEventResponse eventResponse =
                    createEventResponse(
                            List.of(event)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getNextEventsByLeague(
                    "800"
            )).thenReturn(null);

            when(sportsDbClient.getNextEventsByLeague(
                    "900"
            )).thenReturn(eventResponse);

            when(contentSyncService.syncSportsEvents(
                    eventResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            2,
                            1,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectCurrentSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    2,
                                    1,
                                    0
                            )
                    );

            verify(contentSyncService, times(1))
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("예정 경기 목록이 null인 리그는 건너뛴다")
        void collectCurrentSeasonEvents_skipsNullEventsList() {
            // given
            SportsDbLeagueItem league =
                    createLeague(
                            "1000",
                            "리그",
                            "Soccer"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(league)
                    );

            SportsDbEventResponse eventResponse =
                    new SportsDbEventResponse(
                            null
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getNextEventsByLeague(
                    "1000"
            )).thenReturn(eventResponse);

            // when
            ContentSyncResult result =
                    collectionService
                            .collectCurrentSeasonEvents();

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentSyncService, never())
                    .syncSportsEvents(anyList());
        }

        @Test
        @DisplayName("collectUpcomingEvents도 전체 리그의 예정 경기를 수집한다")
        void collectUpcomingEvents_collectsUpcomingLeagueEvents() {
            // given
            SportsDbLeagueItem league =
                    createLeague(
                            "1100",
                            "호환성 리그",
                            "Soccer"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(league)
                    );

            SportsDbEventItem event =
                    createEvent("event-1");

            SportsDbEventResponse eventResponse =
                    createEventResponse(
                            List.of(event)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getNextEventsByLeague(
                    "1100"
            )).thenReturn(eventResponse);

            when(contentSyncService.syncSportsEvents(
                    eventResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            5,
                            4,
                            3
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectUpcomingEvents();

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    5,
                                    4,
                                    3
                            )
                    );

            verify(sportsDbClient)
                    .getNextEventsByLeague("1100");
        }

        @Test
        @DisplayName("중복 리그는 한 번만 예정 경기를 조회한다")
        void collectCurrentSeasonEvents_deduplicatesLeagues() {
            // given
            SportsDbLeagueItem firstLeague =
                    createLeague(
                            "1200",
                            "첫 리그",
                            "Soccer"
                    );

            SportsDbLeagueItem duplicatedLeague =
                    createLeague(
                            "1200",
                            "중복 리그",
                            "Basketball"
                    );

            SportsDbLeagueResponse leagueResponse =
                    createLeagueResponse(
                            List.of(
                                    firstLeague,
                                    duplicatedLeague
                            )
                    );

            SportsDbEventItem event =
                    createEvent("event-1");

            SportsDbEventResponse eventResponse =
                    createEventResponse(
                            List.of(event)
                    );

            when(sportsDbClient.getAllLeagues())
                    .thenReturn(leagueResponse);

            when(sportsDbClient.getNextEventsByLeague(
                    "1200"
            )).thenReturn(eventResponse);

            when(contentSyncService.syncSportsEvents(
                    eventResponse.events()
            )).thenReturn(
                    new ContentSyncResult(
                            1,
                            0,
                            0
                    )
            );

            // when
            ContentSyncResult result =
                    collectionService
                            .collectCurrentSeasonEvents();

            // then
            assertThat(result.createdCount())
                    .isEqualTo(1);

            verify(sportsDbClient, times(1))
                    .getNextEventsByLeague("1200");
        }
    }


    private SportsDbLeagueItem createLeague(
            String leagueId,
            String leagueName,
            String sport
    ) {
        return new SportsDbLeagueItem(
                leagueId,
                leagueName,
                sport,
                null
        );
    }

    private SportsDbLeagueResponse createLeagueResponse(
            List<SportsDbLeagueItem> leagues
    ) {
        return new SportsDbLeagueResponse(
                leagues
        );
    }

    private SportsDbEventResponse createEventResponse(
            List<SportsDbEventItem> events
    ) {
        return new SportsDbEventResponse(
                events
        );
    }

    private SportsDbEventItem createEvent(
            String eventId
    ) {
        return new SportsDbEventItem(
                eventId,
                "테스트 경기",
                "Soccer",
                null,
                "테스트 리그",
                null,
                "2026",
                null,
                "홈팀",
                null,
                null,
                "원정팀",
                null,
                "2026-07-20",
                "18:00:00",
                "테스트 경기장",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

}