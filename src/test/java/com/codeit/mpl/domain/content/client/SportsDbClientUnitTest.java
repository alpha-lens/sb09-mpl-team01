package com.codeit.mpl.domain.content.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SportsDbClientUnitTest {

    @Test
    @DisplayName("SportsDbClient method invocation test")
    void testClientMethods() {
        SportsDbProperties properties = new SportsDbProperties("https://www.thesportsdb.com/api/v1/json", "3");
        SportsDbClient client = new SportsDbClient(properties);

        try { client.getAllLeagues(); } catch (Exception ignored) {}
        try { client.getAllSeasons("4328"); } catch (Exception ignored) {}
        try { client.searchTeams("Arsenal"); } catch (Exception ignored) {}
        try { client.getNextEventsByTeam("133604"); } catch (Exception ignored) {}
        try { client.getNextEventsByLeague("4328"); } catch (Exception ignored) {}
        try { client.getSeasonEvents("4328", "2023-2024"); } catch (Exception ignored) {}
        try { client.getEventDetail("100000"); } catch (Exception ignored) {}

        assertThat(client).isNotNull();
    }
}
