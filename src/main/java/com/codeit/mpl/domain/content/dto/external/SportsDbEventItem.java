package com.codeit.mpl.domain.content.dto.external;

/**
 * TheSportsDB 경기 응답 DTO입니다.
 *
 * 경기 전용 이미지뿐 아니라 팀 배지와 리그 배지까지
 * 수신하여 스포츠 콘텐츠 썸네일 후보로 사용합니다.
 */
public record SportsDbEventItem(
        String idEvent,
        String strEvent,
        String strSport,

        String idLeague,
        String strLeague,
        String strLeagueBadge,

        String strSeason,

        String idHomeTeam,
        String strHomeTeam,
        String strHomeTeamBadge,

        String idAwayTeam,
        String strAwayTeam,
        String strAwayTeamBadge,

        String dateEvent,
        String strTime,
        String strVenue,

        String strThumb,
        String strPoster,
        String strSquare,
        String strFanart,
        String strBanner,

        String strDescriptionEN
) {
}