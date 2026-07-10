package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbSeasonItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbSeasonResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SportsContentCollectionService {

    private static final Pattern FOUR_DIGIT_YEAR_PATTERN =
            Pattern.compile("(\\d{4})");

    private final SportsDbClient sportsDbClient;
    private final ContentSyncService contentSyncService;

    /**
     * 초기 구축용 스포츠 수집입니다.
     *
     * TheSportsDB가 반환하는 모든 유효 리그를 순회하고,
     * 각 리그의 현재 시즌 경기 목록을 동기화합니다.
     */
    public ContentSyncResult collectInitialSeasonEvents() {
        return collectAllCurrentSeasonEvents(
                "INITIAL"
        );
    }

    /**
     * 매일 오전 3시 스포츠 동기화입니다.
     *
     * 매 실행마다 전체 리그를 다시 조회하므로
     * 새 종목과 새 리그도 자동으로 수집 대상에 포함됩니다.
     */
    public ContentSyncResult collectCurrentSeasonEvents() {
        return collectAllCurrentSeasonEvents(
                "DAILY"
        );
    }

    /**
     * 기존 호출부와의 호환성을 위해 유지합니다.
     */
    public ContentSyncResult collectUpcomingEvents() {
        return collectCurrentSeasonEvents();
    }

    /**
     * 임의의 종목 수, 리그 수, 경기 수 제한 없이
     * 전체 리그의 현재 시즌 경기를 동기화합니다.
     */
    private ContentSyncResult collectAllCurrentSeasonEvents(
            String collectionType
    ) {
        ContentSyncResult totalResult =
                ContentSyncResult.empty();

        List<SportsDbLeagueItem> leagues =
                discoverAllLeagues();

        if (leagues.isEmpty()) {
            log.warn(
                    "SportsDB 수집 대상 리그가 없습니다: collectionType={}",
                    collectionType
            );

            return totalResult;
        }

        for (SportsDbLeagueItem league : leagues) {
            String currentSeason =
                    resolveCurrentSeason(league);

            if (!isNotBlank(currentSeason)) {
                log.info(
                        "SportsDB 현재 시즌을 찾지 못해 건너뜁니다: collectionType={}, sport={}, leagueId={}, league={}",
                        collectionType,
                        league.strSport(),
                        league.idLeague(),
                        league.strLeague()
                );

                continue;
            }

            ContentSyncResult seasonResult =
                    collectSeason(
                            league,
                            currentSeason,
                            collectionType
                    );

            totalResult =
                    totalResult.plus(
                            seasonResult
                    );
        }

        log.info(
                "SportsDB 전체 종목 동기화 완료: collectionType={}, leagues={}, created={}, updated={}, skipped={}",
                collectionType,
                leagues.size(),
                totalResult.createdCount(),
                totalResult.updatedCount(),
                totalResult.skippedCount()
        );

        return totalResult;
    }

    /**
     * TheSportsDB 전체 리그 응답에서 유효한 리그를 모두 선택합니다.
     *
     * 같은 idLeague가 중복된 경우에만 한 번 처리합니다.
     */
    private List<SportsDbLeagueItem> discoverAllLeagues() {
        SportsDbLeagueResponse response =
                sportsDbClient.getAllLeagues();

        if (response == null
                || response.leagues() == null
                || response.leagues().isEmpty()) {

            return List.of();
        }

        Map<String, SportsDbLeagueItem> leaguesById =
                new LinkedHashMap<>();

        for (SportsDbLeagueItem league : response.leagues()) {
            if (!isValidLeague(league)) {
                continue;
            }

            leaguesById.putIfAbsent(
                    league.idLeague().trim(),
                    league
            );
        }

        List<SportsDbLeagueItem> leagues =
                List.copyOf(
                        leaguesById.values()
                );

        long sportCount =
                leagues.stream()
                        .map(
                                SportsDbLeagueItem::strSport
                        )
                        .filter(
                                this::isNotBlank
                        )
                        .map(
                                String::trim
                        )
                        .distinct()
                        .count();

        log.info(
                "SportsDB 전체 리그 자동 탐색 완료: received={}, selected={}, sports={}",
                response.leagues().size(),
                leagues.size(),
                sportCount
        );

        return leagues;
    }

    /**
     * 전체 리그 응답의 strCurrentSeason을 우선 사용합니다.
     *
     * 값이 없는 리그는 시즌 목록 API에서 가장 최근 시즌을 선택합니다.
     */
    private String resolveCurrentSeason(
            SportsDbLeagueItem league
    ) {
        if (isNotBlank(
                league.strCurrentSeason()
        )) {
            return league
                    .strCurrentSeason()
                    .trim();
        }

        SportsDbSeasonResponse response =
                sportsDbClient.getAllSeasons(
                        league.idLeague()
                );

        if (response == null
                || response.seasons() == null
                || response.seasons().isEmpty()) {

            return null;
        }

        return response.seasons()
                .stream()
                .filter(item ->
                        item != null
                                && isNotBlank(
                                item.strSeason()
                        )
                )
                .map(
                        SportsDbSeasonItem::strSeason
                )
                .map(
                        String::trim
                )
                .distinct()
                .max(
                        Comparator
                                .comparingInt(
                                        this::extractSeasonYear
                                )
                                .thenComparing(
                                        Comparator.naturalOrder()
                                )
                )
                .orElse(null);
    }

    /**
     * 특정 리그의 현재 시즌 경기 목록을 DB와 동기화합니다.
     */
    private ContentSyncResult collectSeason(
            SportsDbLeagueItem league,
            String season,
            String collectionType
    ) {
        log.info(
                "SportsDB 시즌 수집 시작: collectionType={}, sport={}, leagueId={}, league={}, season={}",
                collectionType,
                league.strSport(),
                league.idLeague(),
                league.strLeague(),
                season
        );

        SportsDbEventResponse response =
                sportsDbClient.getSeasonEvents(
                        league.idLeague(),
                        season
                );

        if (response == null
                || response.events() == null
                || response.events().isEmpty()) {

            log.info(
                    "SportsDB 시즌 수집 결과 없음: collectionType={}, sport={}, leagueId={}, season={}",
                    collectionType,
                    league.strSport(),
                    league.idLeague(),
                    season
            );

            return ContentSyncResult.empty();
        }

        ContentSyncResult seasonResult =
                contentSyncService.syncSportsEvents(
                        response.events()
                );

        log.info(
                "SportsDB 시즌 수집 완료: collectionType={}, sport={}, leagueId={}, season={}, received={}, created={}, updated={}, skipped={}",
                collectionType,
                league.strSport(),
                league.idLeague(),
                season,
                response.events().size(),
                seasonResult.createdCount(),
                seasonResult.updatedCount(),
                seasonResult.skippedCount()
        );

        return seasonResult;
    }

    private boolean isValidLeague(
            SportsDbLeagueItem league
    ) {
        return league != null
                && isNotBlank(
                league.idLeague()
        )
                && isNotBlank(
                league.strLeague()
        )
                && isNotBlank(
                league.strSport()
        );
    }

    /**
     * 시즌 문자열에 포함된 첫 번째 4자리 연도를 추출합니다.
     */
    private int extractSeasonYear(
            String season
    ) {
        if (!isNotBlank(season)) {
            return Integer.MIN_VALUE;
        }

        Matcher matcher =
                FOUR_DIGIT_YEAR_PATTERN.matcher(
                        season
                );

        if (!matcher.find()) {
            return Integer.MIN_VALUE;
        }

        try {
            return Integer.parseInt(
                    matcher.group(1)
            );

        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private boolean isNotBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}