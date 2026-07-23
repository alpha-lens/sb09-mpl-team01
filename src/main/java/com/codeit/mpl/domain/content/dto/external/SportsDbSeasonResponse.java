package com.codeit.mpl.domain.content.dto.external;

import java.util.List;

public record SportsDbSeasonResponse(
        List<SportsDbSeasonItem> seasons
) {
}