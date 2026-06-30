package com.codeit.mpl.domain.content.dto.external;

public record TmdbContentItem(
        Long id,
        String title,
        String name,
        String overview,
        String poster_path,
        String backdrop_path,
        String release_date,
        String first_air_date
) {
}