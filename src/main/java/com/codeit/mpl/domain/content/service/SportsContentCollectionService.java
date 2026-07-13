package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbLeagueResponse;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SportsContentCollectionService {

    /**
     * 콘텐츠 배치 스케줄러와 같은 한국 시간 기준으로
     * 현재 연도를 계산합니다.
     */
    private static final ZoneId COLLECTION_ZONE =
            ZoneId.of("Asia/Seoul");

    private final SportsDbClient sportsDbClient;
    private final ContentSyncService contentSyncService;

    /**
     * 서비스 최초 구축용 스포츠 수집입니다.
     *
     * 전체 리그를 탐색한 뒤 현재 연도를 기준으로
     * 가능한 최신 시즌 후보를 직접 생성합니다.
     *
     * search_all_seasons.php는 최신 시즌을 누락하는 경우가 있으므로
     * 최신 시즌 선택 용도로 사용하지 않습니다.
     */
    public ContentSyncResult collectInitialSeasonEvents() {
        return collectLatestSeasonEvents();
    }

    /**
     * 매일 오전 3시 스포츠 동기화입니다.
     *
     * 전체 시즌을 매일 다시 조회하지 않고,
     * 각 리그의 다음 예정 경기를 조회하여 저장하거나 갱신합니다.
     */
    public ContentSyncResult collectCurrentSeasonEvents() {
        return collectUpcomingEventsByAllLeagues();
    }

    /**
     * 기존 호출부와의 호환성을 위해 유지합니다.
     *
     * 메서드 이름에 맞게 예정 경기 수집을 수행합니다.
     */
    public ContentSyncResult collectUpcomingEvents() {
        return collectUpcomingEventsByAllLeagues();
    }

    /**
     * 초기 구축 시 전체 리그의 최신 시즌 경기를 수집합니다.
     *
     * 리그별로 최신 시즌 후보를 순서대로 조회하고,
     * 경기 데이터가 존재하는 첫 번째 시즌만 수집합니다.
     */
    private ContentSyncResult collectLatestSeasonEvents() {
        ContentSyncResult totalResult =
                ContentSyncResult.empty();

        List<SportsDbLeagueItem> leagues =
                discoverAllLeagues();

        if (leagues.isEmpty()) {
            log.warn(
                    "SportsDB 초기 수집 대상 리그가 없습니다."
            );

            return totalResult;
        }

        List<String> seasonCandidates =
                createSeasonCandidates();

        log.info(
                "SportsDB 초기 최신 시즌 수집 시작: leagues={}, seasonCandidates={}",
                leagues.size(),
                seasonCandidates
        );

        int collectedLeagueCount = 0;
        int skippedLeagueCount = 0;

        for (SportsDbLeagueItem league : leagues) {
            ContentSyncResult leagueResult =
                    collectLatestAvailableSeason(
                            league,
                            seasonCandidates
                    );

            if (leagueResult == null) {
                skippedLeagueCount++;

                log.info(
                        "SportsDB 최신 시즌 경기를 찾지 못해 리그를 건너뜁니다: sport={}, leagueId={}, league={}, candidates={}",
                        league.strSport(),
                        league.idLeague(),
                        league.strLeague(),
                        seasonCandidates
                );

                continue;
            }

            collectedLeagueCount++;

            totalResult =
                    totalResult.plus(
                            leagueResult
                    );
        }

        log.info(
                "SportsDB 초기 최신 시즌 수집 완료: totalLeagues={}, collectedLeagues={}, skippedLeagues={}, created={}, updated={}, skipped={}",
                leagues.size(),
                collectedLeagueCount,
                skippedLeagueCount,
                totalResult.createdCount(),
                totalResult.updatedCount(),
                totalResult.skippedCount()
        );

        return totalResult;
    }

    /**
     * 특정 리그에 대해 최신 시즌 후보부터 순서대로 조회합니다.
     *
     * 경기 데이터가 존재하는 첫 번째 시즌만 동기화하고
     * 나머지 오래된 후보는 조회하지 않습니다.
     *
     * 반환값이 null이면 모든 후보 시즌에서 경기 데이터를
     * 찾지 못했다는 의미입니다.
     */
    private ContentSyncResult collectLatestAvailableSeason(
            SportsDbLeagueItem league,
            List<String> seasonCandidates
    ) {
        for (String season : seasonCandidates) {
            log.info(
                    "SportsDB 시즌 후보 조회: sport={}, leagueId={}, league={}, season={}",
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

            if (!hasEvents(response)) {
                log.debug(
                        "SportsDB 시즌 후보 결과 없음: sport={}, leagueId={}, league={}, season={}",
                        league.strSport(),
                        league.idLeague(),
                        league.strLeague(),
                        season
                );

                continue;
            }

            ContentSyncResult seasonResult =
                    contentSyncService.syncSportsEvents(
                            response.events()
                    );

            log.info(
                    "SportsDB 최신 시즌 수집 완료: sport={}, leagueId={}, league={}, season={}, received={}, created={}, updated={}, skipped={}",
                    league.strSport(),
                    league.idLeague(),
                    league.strLeague(),
                    season,
                    response.events().size(),
                    seasonResult.createdCount(),
                    seasonResult.updatedCount(),
                    seasonResult.skippedCount()
            );

            return seasonResult;
        }

        return null;
    }

    /**
     * 매일 새벽 3시에 전체 리그의 다음 예정 경기를 수집합니다.
     *
     * eventsnextleague.php가 반환한 경기는 externalId를 기준으로
     * 신규 경기면 생성하고 기존 경기면 최신 정보로 갱신합니다.
     */
    private ContentSyncResult collectUpcomingEventsByAllLeagues() {
        ContentSyncResult totalResult =
                ContentSyncResult.empty();

        List<SportsDbLeagueItem> leagues =
                discoverAllLeagues();

        if (leagues.isEmpty()) {
            log.warn(
                    "SportsDB 일일 수집 대상 리그가 없습니다."
            );

            return totalResult;
        }

        log.info(
                "SportsDB 전체 리그 예정 경기 수집 시작: leagues={}",
                leagues.size()
        );

        int collectedLeagueCount = 0;
        int emptyLeagueCount = 0;

        for (SportsDbLeagueItem league : leagues) {
            SportsDbEventResponse response =
                    sportsDbClient.getNextEventsByLeague(
                            league.idLeague()
                    );

            if (!hasEvents(response)) {
                emptyLeagueCount++;

                log.debug(
                        "SportsDB 예정 경기 없음: sport={}, leagueId={}, league={}",
                        league.strSport(),
                        league.idLeague(),
                        league.strLeague()
                );

                continue;
            }

            ContentSyncResult leagueResult =
                    contentSyncService.syncSportsEvents(
                            response.events()
                    );

            totalResult =
                    totalResult.plus(
                            leagueResult
                    );

            collectedLeagueCount++;

            log.info(
                    "SportsDB 리그 예정 경기 수집 완료: sport={}, leagueId={}, league={}, received={}, created={}, updated={}, skipped={}",
                    league.strSport(),
                    league.idLeague(),
                    league.strLeague(),
                    response.events().size(),
                    leagueResult.createdCount(),
                    leagueResult.updatedCount(),
                    leagueResult.skippedCount()
            );
        }

        log.info(
                "SportsDB 전체 리그 예정 경기 수집 완료: totalLeagues={}, collectedLeagues={}, emptyLeagues={}, created={}, updated={}, skipped={}",
                leagues.size(),
                collectedLeagueCount,
                emptyLeagueCount,
                totalResult.createdCount(),
                totalResult.updatedCount(),
                totalResult.skippedCount()
        );

        return totalResult;
    }

    /**
     * TheSportsDB 전체 리그 응답에서 유효한 리그를 선택합니다.
     *
     * 같은 idLeague가 중복된 경우에는 첫 번째 리그만 사용합니다.
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

            String normalizedLeagueId =
                    league.idLeague().trim();

            leaguesById.putIfAbsent(
                    normalizedLeagueId,
                    league
            );
        }

        List<SportsDbLeagueItem> leagues =
                new ArrayList<>(
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

        return List.copyOf(leagues);
    }

    /**
     * 현재 연도를 기준으로 시즌 후보를 생성합니다.
     *
     * 예를 들어 현재 연도가 2026년이라면 다음 순서로 조회합니다.
     *
     * 1. 2026-2027
     * 2. 2025-2026
     * 3. 2026
     * 4. 2025
     *
     * 축구·농구처럼 연도를 걸치는 시즌과
     * 야구·모터스포츠처럼 단일 연도 시즌을 모두 지원합니다.
     */
    private List<String> createSeasonCandidates() {
        int currentYear =
                Year.now(
                        COLLECTION_ZONE
                ).getValue();

        Set<String> candidates =
                new LinkedHashSet<>();

        /*
         * 유럽 축구, 농구 등 연도를 걸치는 시즌 후보입니다.
         */
        candidates.add(
                currentYear
                        + "-"
                        + (currentYear + 1)
        );

        candidates.add(
                (currentYear - 1)
                        + "-"
                        + currentYear
        );

        /*
         * 야구, 모터스포츠 등 단일 연도 시즌 후보입니다.
         */
        candidates.add(
                String.valueOf(
                        currentYear
                )
        );

        candidates.add(
                String.valueOf(
                        currentYear - 1
                )
        );

        return List.copyOf(
                candidates
        );
    }

    /**
     * 경기 응답에 실제 경기 데이터가 존재하는지 확인합니다.
     */
    private boolean hasEvents(
            SportsDbEventResponse response
    ) {
        return response != null
                && response.events() != null
                && !response.events().isEmpty();
    }

    /**
     * 수집 가능한 유효한 리그인지 확인합니다.
     */
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

    private boolean isNotBlank(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}