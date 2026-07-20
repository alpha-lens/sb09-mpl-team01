package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.client.TmdbProperties;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentSourceType;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentSyncService 단위 테스트")
class ContentSyncServiceTest {

    private static final String ADMIN_EMAIL =
            "system@mopl.io";

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TmdbProperties tmdbProperties;

    @Mock
    private User admin;

    private ContentSyncService contentSyncService;

    @BeforeEach
    void setUp() {
        contentSyncService =
                new ContentSyncService(
                        contentRepository,
                        userRepository,
                        tmdbProperties
                );

        ReflectionTestUtils.setField(
                contentSyncService,
                "adminEmail",
                ADMIN_EMAIL
        );
    }

    @Nested
    @DisplayName("TMDB 페이지 동기화")
    class SyncTmdbPageTest {

        @Test
        @DisplayName("sourceItems가 null이면 빈 결과를 반환한다")
        void syncTmdbPage_returnsEmptyWhenItemsAreNull() {
            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.MOVIE,
                            null
                    );

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentRepository, never())
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    );

            verify(userRepository, never())
                    .findByEmail(ADMIN_EMAIL);

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("sourceItems가 비어 있으면 빈 결과를 반환한다")
        void syncTmdbPage_returnsEmptyWhenItemsAreEmpty() {
            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.TVSERIES,
                            List.of()
                    );

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentRepository, never())
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_TV),
                            anySet()
                    );

            verify(userRepository, never())
                    .findByEmail(ADMIN_EMAIL);
        }

        @Test
        @DisplayName("SPORT 타입으로 TMDB 동기화를 요청하면 실패한다")
        void syncTmdbPage_failsWhenTypeIsSport() {
            // when & then
            assertThatThrownBy(() ->
                    contentSyncService.syncTmdbPage(
                            ContentType.SPORT,
                            List.of()
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "TMDB 동기화는 MOVIE 또는 TVSERIES만 지원합니다."
                    );

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("유효하지 않은 영화 항목만 있으면 모두 건너뛴다")
        void syncTmdbPage_skipsAllInvalidMovieItems() {
            // given
            TmdbContentItem idMissingItem =
                    mock(TmdbContentItem.class);

            TmdbContentItem titleMissingItem =
                    mock(TmdbContentItem.class);

            TmdbContentItem blankTitleItem =
                    mock(TmdbContentItem.class);

            when(idMissingItem.id())
                    .thenReturn(null);

            when(titleMissingItem.id())
                    .thenReturn(2L);

            when(titleMissingItem.title())
                    .thenReturn(null);

            when(blankTitleItem.id())
                    .thenReturn(3L);

            when(blankTitleItem.title())
                    .thenReturn("   ");

            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.MOVIE,
                            Arrays.asList(
                                    null,
                                    idMissingItem,
                                    titleMissingItem,
                                    blankTitleItem
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    0,
                                    0,
                                    4
                            )
                    );

            verify(contentRepository, never())
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    );

            verify(userRepository, never())
                    .findByEmail(ADMIN_EMAIL);

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("유효하지 않은 TV 항목만 있으면 모두 건너뛴다")
        void syncTmdbPage_skipsAllInvalidTvItems() {
            // given
            TmdbContentItem nameMissingItem =
                    mock(TmdbContentItem.class);

            TmdbContentItem blankNameItem =
                    mock(TmdbContentItem.class);

            when(nameMissingItem.id())
                    .thenReturn(10L);

            when(nameMissingItem.name())
                    .thenReturn(null);

            when(blankNameItem.id())
                    .thenReturn(11L);

            when(blankNameItem.name())
                    .thenReturn(" ");

            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.TVSERIES,
                            List.of(
                                    nameMissingItem,
                                    blankNameItem
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    0,
                                    0,
                                    2
                            )
                    );

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("신규 영화는 생성하고 기존 영화는 갱신한다")
        void syncTmdbPage_createsAndUpdatesMovies() {
            // given
            TmdbContentItem newMovie =
                    createMovieItem(
                            100L,
                            "신규 영화",
                            "신규 영화 설명",
                            "/new-movie.jpg"
                    );

            TmdbContentItem existingMovieItem =
                    createMovieItem(
                            200L,
                            "수정된 영화",
                            "수정된 영화 설명",
                            "/updated-movie.jpg"
                    );

            TmdbContentItem invalidMovie =
                    mock(TmdbContentItem.class);

            when(invalidMovie.id())
                    .thenReturn(300L);

            when(invalidMovie.title())
                    .thenReturn(" ");

            Content existingContent =
                    mock(Content.class);

            when(existingContent.getExternalId())
                    .thenReturn("200");

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    ))
                    .thenReturn(
                            List.of(existingContent)
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(tmdbProperties.imageBaseUrl())
                    .thenReturn(
                            "https://image.tmdb.org/t/p/w500"
                    );

            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.MOVIE,
                            List.of(
                                    newMovie,
                                    existingMovieItem,
                                    invalidMovie
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    1,
                                    1
                            )
                    );

            verify(contentRepository)
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    );

            verify(userRepository)
                    .findByEmail(ADMIN_EMAIL);

            verify(existingContent)
                    .updateFromExternalApi(
                            "수정된 영화",
                            "수정된 영화 설명",
                            "https://image.tmdb.org/t/p/w500/updated-movie.jpg",
                            "https://www.themoviedb.org/movie/200",
                            List.of("MOVIE")
                    );

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Content>> captor =
                    ArgumentCaptor.forClass(List.class);

            verify(contentRepository)
                    .saveAll(captor.capture());

            assertThat(captor.getValue())
                    .hasSize(1);
        }

        @Test
        @DisplayName("신규 TV 시리즈를 생성한다")
        void syncTmdbPage_createsTvSeries() {
            // given
            TmdbContentItem tvItem =
                    createTvItem(
                            500L,
                            "신규 TV 시리즈",
                            "TV 시리즈 설명",
                            "/tv.jpg"
                    );

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_TV),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(tmdbProperties.imageBaseUrl())
                    .thenReturn(
                            "https://image.tmdb.org/t/p/w500"
                    );

            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.TVSERIES,
                            List.of(tvItem)
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            verify(contentRepository)
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_TV),
                            anySet()
                    );

            verify(contentRepository)
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("모든 영화가 기존 콘텐츠이면 saveAll을 호출하지 않는다")
        void syncTmdbPage_doesNotSaveWhenAllMoviesExist() {
            // given
            TmdbContentItem movieItem =
                    createMovieItem(
                            700L,
                            "기존 영화",
                            "기존 영화 설명",
                            null
                    );

            Content existingContent =
                    mock(Content.class);

            when(existingContent.getExternalId())
                    .thenReturn("700");

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    ))
                    .thenReturn(
                            List.of(existingContent)
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.MOVIE,
                            List.of(movieItem)
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    0,
                                    1,
                                    0
                            )
                    );

            verify(existingContent)
                    .updateFromExternalApi(
                            "기존 영화",
                            "기존 영화 설명",
                            null,
                            "https://www.themoviedb.org/movie/700",
                            List.of("MOVIE")
                    );

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("같은 TMDB ID가 중복되면 마지막 항목만 동기화한다")
        void syncTmdbPage_deduplicatesItemsByExternalId() {
            // given
            TmdbContentItem firstItem =
                    createMovieItem(
                            900L,
                            "첫 번째 제목",
                            "첫 번째 설명",
                            null
                    );

            TmdbContentItem secondItem =
                    createMovieItem(
                            900L,
                            "마지막 제목",
                            "마지막 설명",
                            null
                    );

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            // when
            ContentSyncResult result =
                    contentSyncService.syncTmdbPage(
                            ContentType.MOVIE,
                            List.of(
                                    firstItem,
                                    secondItem
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Content>> captor =
                    ArgumentCaptor.forClass(List.class);

            verify(contentRepository)
                    .saveAll(captor.capture());

            assertThat(captor.getValue())
                    .hasSize(1);
        }

        @Test
        @DisplayName("배치 관리자 계정이 없으면 동기화에 실패한다")
        void syncTmdbPage_failsWhenAdminDoesNotExist() {
            // given
            TmdbContentItem movieItem =
                    createMovieItem(
                            1000L,
                            "영화",
                            "설명",
                            null
                    );

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.TMDB_MOVIE),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.empty()
            );

            // when & then
            assertThatThrownBy(() ->
                    contentSyncService.syncTmdbPage(
                            ContentType.MOVIE,
                            List.of(movieItem)
                    )
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                            "콘텐츠 배치용 관리자 계정이 존재하지 않습니다: "
                                    + ADMIN_EMAIL
                    );

            verify(contentRepository, never())
                    .saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("SportsDB 경기 동기화")
    class SyncSportsEventsTest {

        @Test
        @DisplayName("sourceEvents가 null이면 빈 결과를 반환한다")
        void syncSportsEvents_returnsEmptyWhenEventsAreNull() {
            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            null
                    );

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentRepository, never())
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    );

            verify(userRepository, never())
                    .findByEmail(ADMIN_EMAIL);
        }

        @Test
        @DisplayName("sourceEvents가 비어 있으면 빈 결과를 반환한다")
        void syncSportsEvents_returnsEmptyWhenEventsAreEmpty() {
            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            List.of()
                    );

            // then
            assertThat(result)
                    .isEqualTo(ContentSyncResult.empty());

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("유효하지 않은 스포츠 경기만 있으면 모두 건너뛴다")
        void syncSportsEvents_skipsAllInvalidEvents() {
            // given
            SportsDbEventItem missingId =
                    mock(SportsDbEventItem.class);

            SportsDbEventItem blankId =
                    mock(SportsDbEventItem.class);

            SportsDbEventItem missingTitle =
                    mock(SportsDbEventItem.class);

            SportsDbEventItem blankTitle =
                    mock(SportsDbEventItem.class);

            when(missingId.idEvent())
                    .thenReturn(null);

            when(blankId.idEvent())
                    .thenReturn(" ");

            when(missingTitle.idEvent())
                    .thenReturn("3");

            when(missingTitle.strEvent())
                    .thenReturn(null);

            when(blankTitle.idEvent())
                    .thenReturn("4");

            when(blankTitle.strEvent())
                    .thenReturn(" ");

            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            Arrays.asList(
                                    null,
                                    missingId,
                                    blankId,
                                    missingTitle,
                                    blankTitle
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    0,
                                    0,
                                    5
                            )
                    );

            verify(contentRepository, never())
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    );

            verify(contentRepository, never())
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("신규 스포츠 경기는 생성하고 기존 경기는 갱신한다")
        void syncSportsEvents_createsAndUpdatesEvents() {
            // given
            SportsDbEventItem newEvent =
                    createSportsEvent(
                            "100",
                            "서울 vs 부산",
                            "축구",
                            "K League",
                            "2026",
                            "https://image/new-thumb.jpg"
                    );

            SportsDbEventItem existingEvent =
                    createSportsEvent(
                            "200",
                            "인천 vs 수원",
                            "축구",
                            "K League",
                            "2026",
                            null
                    );

            SportsDbEventItem invalidEvent =
                    mock(SportsDbEventItem.class);

            when(invalidEvent.idEvent())
                    .thenReturn("300");

            when(invalidEvent.strEvent())
                    .thenReturn(" ");

            Content existingContent =
                    mock(Content.class);

            when(existingContent.getExternalId())
                    .thenReturn("200");

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    ))
                    .thenReturn(
                            List.of(existingContent)
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            List.of(
                                    newEvent,
                                    existingEvent,
                                    invalidEvent
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    1,
                                    1
                            )
                    );

            verify(existingContent)
                    .updateFromExternalApi(
                            eq("인천 vs 수원"),
                            org.mockito.ArgumentMatchers.anyString(),
                            eq(null),
                            eq("https://www.thesportsdb.com/event/200"),
                            eq(
                                    List.of(
                                            "SPORT",
                                            "축구",
                                            "K League",
                                            "2026"
                                    )
                            )
                    );

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Content>> captor =
                    ArgumentCaptor.forClass(List.class);

            verify(contentRepository)
                    .saveAll(captor.capture());

            assertThat(captor.getValue())
                    .hasSize(1);
        }

        @Test
        @DisplayName("스포츠 이미지가 없더라도 신규 경기를 저장한다")
        void syncSportsEvents_savesEventWithoutImage() {
            // given
            SportsDbEventItem event =
                    createSportsEvent(
                            "500",
                            "이미지 없는 경기",
                            "Basketball",
                            "NBA",
                            "2026",
                            null
                    );

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            List.of(event)
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            verify(contentRepository)
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("경기 썸네일이 없으면 포스터를 사용한다")
        void syncSportsEvents_usesPosterWhenThumbnailIsMissing() {
            // given
            SportsDbEventItem event =
                    mock(SportsDbEventItem.class);

            when(event.idEvent())
                    .thenReturn("600");

            when(event.strEvent())
                    .thenReturn("포스터 우선순위 경기");

            when(event.strThumb())
                    .thenReturn(" ");

            when(event.strPoster())
                    .thenReturn("  https://image/poster.jpg  ");

            when(event.strSport())
                    .thenReturn("Soccer");

            when(event.strLeague())
                    .thenReturn("Premier League");

            when(event.strSeason())
                    .thenReturn("2026-2027");

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            List.of(event)
                    );

            // then
            assertThat(result.createdCount())
                    .isEqualTo(1);

            verify(contentRepository)
                    .saveAll(anyList());
        }

        @Test
        @DisplayName("같은 스포츠 경기 ID가 중복되면 마지막 항목만 동기화한다")
        void syncSportsEvents_deduplicatesByExternalId() {
            // given
            SportsDbEventItem firstEvent =
                    createSportsEvent(
                            "700",
                            "첫 번째 경기명",
                            "Soccer",
                            "League",
                            "2026",
                            null
                    );

            SportsDbEventItem secondEvent =
                    createSportsEvent(
                            "700",
                            "마지막 경기명",
                            "Soccer",
                            "League",
                            "2026",
                            null
                    );

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            // when
            ContentSyncResult result =
                    contentSyncService.syncSportsEvents(
                            List.of(
                                    firstEvent,
                                    secondEvent
                            )
                    );

            // then
            assertThat(result)
                    .isEqualTo(
                            new ContentSyncResult(
                                    1,
                                    0,
                                    0
                            )
                    );

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Content>> captor =
                    ArgumentCaptor.forClass(List.class);

            verify(contentRepository)
                    .saveAll(captor.capture());

            assertThat(captor.getValue())
                    .hasSize(1);
        }

        @Test
        @DisplayName("배치 관리자 계정이 없으면 스포츠 동기화에 실패한다")
        void syncSportsEvents_failsWhenAdminDoesNotExist() {
            // given
            SportsDbEventItem event =
                    createSportsEvent(
                            "800",
                            "경기",
                            "Soccer",
                            "League",
                            "2026",
                            null
                    );

            when(contentRepository
                    .findAllBySourceTypeAndExternalIdIn(
                            eq(ContentSourceType.THE_SPORTS_DB),
                            anySet()
                    ))
                    .thenReturn(List.of());

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.empty()
            );

            // when & then
            assertThatThrownBy(() ->
                    contentSyncService.syncSportsEvents(
                            List.of(event)
                    )
            )
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage(
                            "콘텐츠 배치용 관리자 계정이 존재하지 않습니다: "
                                    + ADMIN_EMAIL
                    );

            verify(contentRepository, never())
                    .saveAll(anyList());
        }
    }


    private TmdbContentItem createMovieItem(
            long id,
            String title,
            String overview,
            String posterPath
    ) {
        return new TmdbContentItem(
                id,
                title,
                null,
                overview,
                posterPath,
                null,
                null,
                null
        );
    }

    private TmdbContentItem createTvItem(
            long id,
            String name,
            String overview,
            String posterPath
    ) {
        return new TmdbContentItem(
                id,
                null,
                name,
                overview,
                posterPath,
                null,
                null,
                null
        );
    }

    private SportsDbEventItem createSportsEvent(
            String externalId,
            String eventName,
            String sport,
            String league,
            String season,
            String thumbnailUrl
    ) {
        return new SportsDbEventItem(
                externalId,
                eventName,
                sport,
                null,
                league,
                null,
                season,
                null,
                "홈팀",
                null,
                null,
                "원정팀",
                null,
                "2026-07-20",
                "18:00:00",
                "경기장",
                thumbnailUrl,
                null,
                null,
                null,
                null,
                "경기 설명"
        );
    }
}