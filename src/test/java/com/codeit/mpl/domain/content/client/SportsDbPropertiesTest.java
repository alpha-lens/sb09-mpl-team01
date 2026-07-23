package com.codeit.mpl.domain.content.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SportsDbPropertiesTest {

    @Test
    @DisplayName("SportsDbProperties getter test")
    void testProperties() {
        SportsDbProperties properties = new SportsDbProperties("https://www.thesportsdb.com/api/v1/json", "3");
        assertThat(properties.baseUrl()).isEqualTo("https://www.thesportsdb.com/api/v1/json");
        assertThat(properties.apiKey()).isEqualTo("3");
    }

    @Test
    @DisplayName("SportsDbClient initialization test")
    void testClientInitialization() {
        SportsDbProperties properties = new SportsDbProperties("https://www.thesportsdb.com/api/v1/json", "3");
        SportsDbClient client = new SportsDbClient(properties);
        assertThat(client).isNotNull();
    }
}
