package com.codeit.mpl.domain.content.dto.external;

import java.util.List;

public record TmdbSearchResponse(
        int page,
        List<TmdbContentItem> results,
        int total_pages,
        int total_results
) {
}