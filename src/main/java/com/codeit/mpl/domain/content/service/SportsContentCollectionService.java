package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.config.ContentCollectionProperties;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SportsContentCollectionService {

    private final SportsDbClient sportsDbClient;
    private final ContentSyncService contentSyncService;
    private final ContentCollectionProperties properties;

    public ContentSyncResult collectUpcomingEvents() {
        ContentSyncResult totalResult =
                ContentSyncResult.empty();

        for (String leagueId
                : properties.sports().leagueIds()) {

            SportsDbEventResponse response =
                    sportsDbClient.getNextEventsByLeague(
                            leagueId
                    );

            if (response == null
                    || response.events() == null
                    || response.events().isEmpty()) {

                log.info(
                        "SportsDB 수집 결과 없음: leagueId={}",
                        leagueId
                );

                continue;
            }

            ContentSyncResult leagueResult =
                    contentSyncService.syncSportsEvents(
                            response.events()
                    );

            totalResult =
                    totalResult.plus(leagueResult);

            log.info(
                    "SportsDB 리그 수집 완료: leagueId={}, created={}, updated={}, skipped={}",
                    leagueId,
                    leagueResult.createdCount(),
                    leagueResult.updatedCount(),
                    leagueResult.skippedCount()
            );
        }

        return totalResult;
    }
}