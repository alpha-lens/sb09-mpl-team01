package com.codeit.mpl.domain.content.dto.external;

public record SportsDbEventItem(
        String idEvent,
        String strEvent,
        String strSport,
        String strLeague,
        String strSeason,
        String strHomeTeam,
        String strAwayTeam,
        String dateEvent,
        String strTime,
        String strVenue,
        String strThumb,
        String strBanner,
        String strPoster,
        String strDescriptionEN
) {
}