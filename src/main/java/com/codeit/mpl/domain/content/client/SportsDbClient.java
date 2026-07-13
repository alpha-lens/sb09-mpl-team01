package com.codeit.mpl.domain.content.client;

import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbSeasonResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbTeamResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
@RequiredArgsConstructor
public class SportsDbClient {

    private final SportsDbProperties sportsDbProperties;

    /**
     * SportsDB API 호출용 RestClient를 생성합니다.
     *
     * content.collection.sports 설정과 무관하며,
     * 최상위 sportsdb.base-url 및 sportsdb.api-key를 사용합니다.
     */
    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Duration.ofSeconds(3)
        );

        requestFactory.setReadTimeout(
                Duration.ofSeconds(15)
        );

        return RestClient.builder()
                .baseUrl(
                        sportsDbProperties.baseUrl()
                                + "/"
                                + sportsDbProperties.apiKey()
                )
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * TheSportsDB가 반환하는 전체 리그 목록을 조회합니다.
     */
    public SportsDbLeagueResponse getAllLeagues() {
        try {
            SportsDbLeagueResponse response =
                    restClient()
                            .get()
                            .uri("/all_leagues.php")
                            .retrieve()
                            .body(
                                    SportsDbLeagueResponse.class
                            );

            if (response == null
                    || response.leagues() == null) {

                return emptyLeagueResponse();
            }

            return response;

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 전체 리그 조회 실패",
                    e
            );

            return emptyLeagueResponse();
        }
    }

    /**
     * 특정 리그의 시즌 목록을 조회합니다.
     */
    public SportsDbSeasonResponse getAllSeasons(
            String leagueId
    ) {
        try {
            SportsDbSeasonResponse response =
                    restClient()
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path(
                                                    "/search_all_seasons.php"
                                            )
                                            .queryParam(
                                                    "id",
                                                    leagueId
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    SportsDbSeasonResponse.class
                            );

            if (response == null
                    || response.seasons() == null) {

                return emptySeasonResponse();
            }

            return response;

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 시즌 목록 조회 실패: leagueId={}",
                    leagueId,
                    e
            );

            return emptySeasonResponse();
        }
    }

    /**
     * 관리자 수동 검색용 팀 검색입니다.
     */
    public SportsDbTeamResponse searchTeams(
            String keyword
    ) {
        try {
            SportsDbTeamResponse response =
                    restClient()
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path(
                                                    "/searchteams.php"
                                            )
                                            .queryParam(
                                                    "t",
                                                    keyword
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    SportsDbTeamResponse.class
                            );

            return response == null
                    ? new SportsDbTeamResponse(
                    List.of()
            )
                    : response;

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 팀 검색 실패: keyword={}",
                    keyword,
                    e
            );

            return new SportsDbTeamResponse(
                    List.of()
            );
        }
    }

    /**
     * 특정 팀의 다음 경기를 조회합니다.
     */
    public SportsDbEventResponse getNextEventsByTeam(
            String teamId
    ) {
        try {
            SportsDbEventResponse response =
                    restClient()
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path(
                                                    "/eventsnext.php"
                                            )
                                            .queryParam(
                                                    "id",
                                                    teamId
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    SportsDbEventResponse.class
                            );

            return emptyIfNull(response);

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 팀 예정 경기 조회 실패: teamId={}",
                    teamId,
                    e
            );

            return emptyEventResponse();
        }
    }

    /**
     * 특정 리그의 다음 경기를 조회합니다.
     */
    public SportsDbEventResponse getNextEventsByLeague(
            String leagueId
    ) {
        try {
            SportsDbEventResponse response =
                    restClient()
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path(
                                                    "/eventsnextleague.php"
                                            )
                                            .queryParam(
                                                    "id",
                                                    leagueId
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    SportsDbEventResponse.class
                            );

            return emptyIfNull(response);

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 리그 예정 경기 조회 실패: leagueId={}",
                    leagueId,
                    e
            );

            return emptyEventResponse();
        }
    }

    /**
     * 특정 리그의 특정 시즌 경기 목록을 조회합니다.
     */
    public SportsDbEventResponse getSeasonEvents(
            String leagueId,
            String season
    ) {
        try {
            SportsDbEventResponse response =
                    restClient()
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path(
                                                    "/eventsseason.php"
                                            )
                                            .queryParam(
                                                    "id",
                                                    leagueId
                                            )
                                            .queryParam(
                                                    "s",
                                                    season
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    SportsDbEventResponse.class
                            );

            return emptyIfNull(response);

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 시즌 경기 조회 실패: leagueId={}, season={}",
                    leagueId,
                    season,
                    e
            );

            return emptyEventResponse();
        }
    }

    /**
     * 스포츠 경기 상세를 조회합니다.
     */
    public SportsDbEventResponse getEventDetail(
            String eventId
    ) {
        try {
            SportsDbEventResponse response =
                    restClient()
                            .get()
                            .uri(uriBuilder ->
                                    uriBuilder
                                            .path(
                                                    "/lookupevent.php"
                                            )
                                            .queryParam(
                                                    "id",
                                                    eventId
                                            )
                                            .build()
                            )
                            .retrieve()
                            .body(
                                    SportsDbEventResponse.class
                            );

            return emptyIfNull(response);

        } catch (RestClientException e) {
            log.warn(
                    "SportsDB 경기 상세 조회 실패: eventId={}",
                    eventId,
                    e
            );

            return emptyEventResponse();
        }
    }

    private SportsDbEventResponse emptyIfNull(
            SportsDbEventResponse response
    ) {
        if (response == null
                || response.events() == null) {

            return emptyEventResponse();
        }

        return response;
    }

    private SportsDbEventResponse emptyEventResponse() {
        return new SportsDbEventResponse(
                List.of()
        );
    }

    private SportsDbLeagueResponse emptyLeagueResponse() {
        return new SportsDbLeagueResponse(
                List.of()
        );
    }

    private SportsDbSeasonResponse emptySeasonResponse() {
        return new SportsDbSeasonResponse(
                List.of()
        );
    }
}
