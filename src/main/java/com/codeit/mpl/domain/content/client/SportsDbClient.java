package com.codeit.mpl.domain.content.client;

import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbTeamResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class SportsDbClient {

    private final SportsDbProperties sportsDbProperties;

    private RestClient restClient() {
        return RestClient.builder()
                .baseUrl(sportsDbProperties.baseUrl() + "/" + sportsDbProperties.apiKey())
                .build();
    }

    public SportsDbTeamResponse searchTeams(String keyword) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/searchteams.php")
                        .queryParam("t", keyword)
                        .build()
                )
                .retrieve()
                .body(SportsDbTeamResponse.class);
    }

    public SportsDbEventResponse getNextEventsByTeam(String teamId) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/eventsnext.php")
                        .queryParam("id", teamId)
                        .build()
                )
                .retrieve()
                .body(SportsDbEventResponse.class);
    }

    public SportsDbEventResponse getEventDetail(String eventId) {
        return restClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/lookupevent.php")
                        .queryParam("id", eventId)
                        .build()
                )
                .retrieve()
                .body(SportsDbEventResponse.class);
    }
}