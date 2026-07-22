package com.codeit.mpl.domain.content.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPropertiesTest {

    @Test
    @DisplayName("TmdbProperties getter test")
    void testProperties() {
        TmdbProperties properties = new TmdbProperties("https://api.themoviedb.org/3", "https://image.tmdb.org/t/p/original", "test-token");
        assertThat(properties.baseUrl()).isEqualTo("https://api.themoviedb.org/3");
        assertThat(properties.imageBaseUrl()).isEqualTo("https://image.tmdb.org/t/p/original");
        assertThat(properties.bearerToken()).isEqualTo("test-token");
    }

    @Test
    @DisplayName("TmdbClient initialization test")
    void testClientInitialization() {
        TmdbProperties properties = new TmdbProperties("https://api.themoviedb.org/3", "https://image.tmdb.org/t/p/original", "test-token");
        TmdbClient client = new TmdbClient(properties);
        assertThat(client).isNotNull();
    }
}
