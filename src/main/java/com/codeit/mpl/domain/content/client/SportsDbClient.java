package com.codeit.mpl.domain.content.client;

import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbTeamResponse;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
public class SportsDbClient {

    private final SportsDbProperties sportsDbProperties;

    private RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        return RestClient.builder()
                .baseUrl(sportsDbProperties.baseUrl() + "/" + sportsDbProperties.apiKey())
                .requestFactory(requestFactory)
                .build();
    }

    public SportsDbTeamResponse searchTeams(String keyword) {
        try {
            SportsDbTeamResponse response = restClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/searchteams.php")
                            .queryParam("t", keyword)
                            .build()
                    )
                    .retrieve()
                    .body(SportsDbTeamResponse.class);

            return response == null
                    ? new SportsDbTeamResponse(List.of())
                    : response;
        } catch (RestClientException e) {
            return new SportsDbTeamResponse(List.of());
        }
    }

    public SportsDbEventResponse getNextEventsByTeam(String teamId) {
        try {
            SportsDbEventResponse response = restClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/eventsnext.php")
                            .queryParam("id", teamId)
                            .build()
                    )
                    .retrieve()
                    .body(SportsDbEventResponse.class);

            return response == null
                    ? new SportsDbEventResponse(List.of())
                    : response;
        } catch (RestClientException e) {
            return new SportsDbEventResponse(List.of());
        }
    }

    public SportsDbEventResponse getEventDetail(String eventId) {
        try {
            SportsDbEventResponse response = restClient()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/lookupevent.php")
                            .queryParam("id", eventId)
                            .build()
                    )
                    .retrieve()
                    .body(SportsDbEventResponse.class);

            return response == null
                    ? new SportsDbEventResponse(List.of())
                    : response;
        } catch (RestClientException e) {
            return new SportsDbEventResponse(List.of());
        }
    }
}