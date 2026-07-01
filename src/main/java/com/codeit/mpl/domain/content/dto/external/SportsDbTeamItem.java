package com.codeit.mpl.domain.content.dto.external;

public record SportsDbTeamItem(
        String idTeam,
        String strTeam,
        String strSport,
        String strLeague,
        String strCountry,
        String strDescriptionEN,
        String strTeamBadge
) {
}