package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.external.SportsDbTeamResponse;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.dto.external.TmdbGenre;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
import com.codeit.mpl.domain.content.dto.query.ContentQueryRow;
import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.event.ContentEvent;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentService 단위 테스트")
class ContentServiceTest {

    private static final String ADMIN_EMAIL = "admin@mopl.io";
    private static final String USER_EMAIL = "user@mopl.io";
    private static final String TMDB_SOURCE_TYPE = "TMDB";
    private static final String SPORTS_DB_SOURCE_TYPE = "THE_SPORTS_DB";

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private SportsDbClient sportsDbClient;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ContentSearchService contentSearchService;

    @Mock
    private User admin;

    @Mock
    private User user;

    @Mock
    private User otherUser;

    @Mock
    private Content content;

    @Mock
    private ContentDto contentDto;

    @Mock
    private ContentSummary contentSummary;

    private ContentService contentService;

    @BeforeEach
    void setUp() {
        contentService = new ContentService(
                contentRepository,
                userRepository,
                contentMapper,
                tmdbClient,
                sportsDbClient,
                eventPublisher,
                contentSearchService
        );

        when(admin.getRole()).thenReturn(UserRole.ADMIN);
        when(user.getRole()).thenReturn(UserRole.USER);
        when(otherUser.getRole()).thenReturn(UserRole.USER);

        when(content.getAverageRating()).thenReturn(0.0);
        when(content.getReviewCount()).thenReturn(0);
        when(content.getWatcherCount()).thenReturn(0L);

        when(contentMapper.toDto(
                any(Content.class),
                anyDouble(),
                anyInt(),
                anyLong()
        )).thenReturn(contentDto);

        when(contentMapper.toSummary(
                any(Content.class),
                anyDouble(),
                anyInt(),
                anyLong()
        )).thenReturn(contentSummary);
    }

    @Nested
    @DisplayName("콘텐츠 직접 생성")
    class CreateContentTest {

        @Test
        @DisplayName("관리자는 콘텐츠를 생성할 수 있다")
        void createContent_success() {
            ContentCreateRequest request =
                    mock(ContentCreateRequest.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(request.type()).thenReturn(ContentType.MOVIE);
            when(request.title()).thenReturn("인터스텔라");
            when(request.description()).thenReturn("우주 탐사 영화");
            when(request.tags()).thenReturn(List.of("SF", "MOVIE"));
            when(contentRepository.save(any(Content.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ContentDto result =
                    contentService.createContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(contentRepository).save(any(Content.class));
            verify(eventPublisher)
                    .publishEvent(any(ContentEvent.class));
            verify(contentMapper).toDto(
                    any(Content.class),
                    eq(0.0),
                    eq(0),
                    eq(0L)
            );
        }

        @Test
        @DisplayName("일반 사용자는 콘텐츠를 생성할 수 없다")
        void createContent_failsWhenRequesterIsNotAdmin() {
            ContentCreateRequest request =
                    mock(ContentCreateRequest.class);

            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(
                    () -> contentService.createContent(USER_EMAIL, request)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("관리자만 콘텐츠를 등록할 수 있습니다.");

            verify(contentRepository, never())
                    .save(any(Content.class));
            verify(eventPublisher, never())
                    .publishEvent(any());
        }

        @Test
        @DisplayName("이메일이 null이면 생성에 실패한다")
        void createContent_failsWhenEmailIsNull() {
            ContentCreateRequest request =
                    mock(ContentCreateRequest.class);

            assertThatThrownBy(
                    () -> contentService.createContent(null, request)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("인증 정보가 유효하지 않습니다.");

            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("이메일이 공백이면 생성에 실패한다")
        void createContent_failsWhenEmailIsBlank() {
            ContentCreateRequest request =
                    mock(ContentCreateRequest.class);

            assertThatThrownBy(
                    () -> contentService.createContent("   ", request)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("인증 정보가 유효하지 않습니다.");
        }

        @Test
        @DisplayName("요청자가 존재하지 않으면 생성에 실패한다")
        void createContent_failsWhenRequesterDoesNotExist() {
            ContentCreateRequest request =
                    mock(ContentCreateRequest.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> contentService.createContent(ADMIN_EMAIL, request)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("존재하지 않는 사용자입니다.");

            verify(contentRepository, never())
                    .save(any(Content.class));
        }
    }

    @Nested
    @DisplayName("외부 콘텐츠 가져오기")
    class ImportExternalContentTest {

        @Test
        @DisplayName("이미 저장된 영화는 새로 저장하지 않고 반환한다")
        void importMovie_returnsExistingContent() {
            ContentImportRequest request =
                    new ContentImportRequest("157336", ContentType.MOVIE);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    TMDB_SOURCE_TYPE,
                    "157336"
            )).thenReturn(Optional.of(content));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(tmdbClient, never()).getMovieDetail(any());
            verify(contentRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("이미 저장된 TV 시리즈는 새로 저장하지 않고 반환한다")
        void importTv_returnsExistingContent() {
            ContentImportRequest request =
                    new ContentImportRequest("1396", ContentType.TVSERIES);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    TMDB_SOURCE_TYPE,
                    "1396"
            )).thenReturn(Optional.of(content));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(tmdbClient, never()).getTvSeriesDetail(any());
        }

        @Test
        @DisplayName("이미 저장된 스포츠 경기는 새로 저장하지 않고 반환한다")
        void importSport_returnsExistingContent() {
            ContentImportRequest request =
                    new ContentImportRequest("12345", ContentType.SPORT);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    SPORTS_DB_SOURCE_TYPE,
                    "12345"
            )).thenReturn(Optional.of(content));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(sportsDbClient, never()).getEventDetail(any());
        }

        @Test
        @DisplayName("신규 영화를 실제 TMDB 장르와 함께 가져와 저장한다")
        void importMovie_success() {
            ContentImportRequest request =
                    new ContentImportRequest("157336", ContentType.MOVIE);
            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    TMDB_SOURCE_TYPE,
                    "157336"
            )).thenReturn(Optional.empty());
            when(tmdbClient.getMovieDetail("157336"))
                    .thenReturn(item);
            when(item.title()).thenReturn("인터스텔라");
            when(item.overview()).thenReturn("우주 탐사 영화");
            when(item.poster_path()).thenReturn("/poster.jpg");
            when(item.genres()).thenReturn(List.of(
                    new TmdbGenre(18, "드라마"),
                    new TmdbGenre(878, "SF"),
                    new TmdbGenre(18, "드라마"),
                    new TmdbGenre(0, " "),
                    new TmdbGenre(1, null)
            ));
            when(contentRepository.saveAndFlush(any(Content.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(tmdbClient).getMovieDetail("157336");

            ArgumentCaptor<Content> contentCaptor =
                    ArgumentCaptor.forClass(Content.class);

            verify(contentRepository)
                    .saveAndFlush(contentCaptor.capture());

            assertThat(contentCaptor.getValue().getTags())
                    .containsExactly("드라마", "SF");

            verify(eventPublisher)
                    .publishEvent(any(ContentEvent.class));
        }

        @Test
        @DisplayName("신규 TV 시리즈를 가져와 저장한다")
        void importTv_success() {
            ContentImportRequest request =
                    new ContentImportRequest("1396", ContentType.TVSERIES);
            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    TMDB_SOURCE_TYPE,
                    "1396"
            )).thenReturn(Optional.empty());
            when(tmdbClient.getTvSeriesDetail("1396"))
                    .thenReturn(item);
            when(item.name()).thenReturn("Breaking Bad");
            when(item.overview()).thenReturn("TV 시리즈");
            when(item.poster_path()).thenReturn("/tv.jpg");
            when(contentRepository.saveAndFlush(any(Content.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(tmdbClient).getTvSeriesDetail("1396");
            verify(contentRepository).saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("신규 스포츠 경기를 가져와 저장한다")
        void importSport_success() {
            ContentImportRequest request =
                    new ContentImportRequest("12345", ContentType.SPORT);
            SportsDbEventItem event =
                    mock(SportsDbEventItem.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    SPORTS_DB_SOURCE_TYPE,
                    "12345"
            )).thenReturn(Optional.empty());
            when(sportsDbClient.getEventDetail("12345"))
                    .thenReturn(new SportsDbEventResponse(List.of(event)));
            when(event.strEvent()).thenReturn("서울 vs 부산");
            when(event.strSport()).thenReturn("Soccer");
            when(event.strLeague()).thenReturn("K League");
            when(event.strSeason()).thenReturn("2026");
            when(event.strHomeTeam()).thenReturn("서울");
            when(event.strAwayTeam()).thenReturn("부산");
            when(event.dateEvent()).thenReturn("2026-07-20");
            when(event.strTime()).thenReturn("18:00:00");
            when(event.strVenue()).thenReturn("서울 경기장");
            when(event.strDescriptionEN()).thenReturn("경기 설명");
            when(event.strThumb()).thenReturn("https://image/thumb.jpg");
            when(contentRepository.saveAndFlush(any(Content.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(sportsDbClient).getEventDetail("12345");
            verify(contentRepository).saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("스포츠 응답이 null이면 가져오기에 실패한다")
        void importSport_failsWhenResponseIsNull() {
            ContentImportRequest request =
                    new ContentImportRequest("missing", ContentType.SPORT);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    SPORTS_DB_SOURCE_TYPE,
                    "missing"
            )).thenReturn(Optional.empty());
            when(sportsDbClient.getEventDetail("missing"))
                    .thenReturn(null);

            assertThatThrownBy(
                    () -> contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("존재하지 않는 스포츠 경기입니다.");
        }

        @Test
        @DisplayName("스포츠 목록이 null이면 가져오기에 실패한다")
        void importSport_failsWhenEventsAreNull() {
            ContentImportRequest request =
                    new ContentImportRequest("missing", ContentType.SPORT);
            SportsDbEventResponse response =
                    mock(SportsDbEventResponse.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    SPORTS_DB_SOURCE_TYPE,
                    "missing"
            )).thenReturn(Optional.empty());
            when(sportsDbClient.getEventDetail("missing"))
                    .thenReturn(response);
            when(response.events()).thenReturn(null);

            assertThatThrownBy(
                    () -> contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("존재하지 않는 스포츠 경기입니다.");
        }

        @Test
        @DisplayName("스포츠 목록이 비어 있으면 가져오기에 실패한다")
        void importSport_failsWhenEventsAreEmpty() {
            ContentImportRequest request =
                    new ContentImportRequest("missing", ContentType.SPORT);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    SPORTS_DB_SOURCE_TYPE,
                    "missing"
            )).thenReturn(Optional.empty());
            when(sportsDbClient.getEventDetail("missing"))
                    .thenReturn(new SportsDbEventResponse(List.of()));

            assertThatThrownBy(
                    () -> contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("존재하지 않는 스포츠 경기입니다.");
        }

        @Test
        @DisplayName("동시 저장 충돌 후 기존 콘텐츠를 반환한다")
        void importMovie_returnsExistingAfterDuplicate() {
            ContentImportRequest request =
                    new ContentImportRequest("157336", ContentType.MOVIE);
            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    TMDB_SOURCE_TYPE,
                    "157336"
            )).thenReturn(Optional.empty(), Optional.of(content));
            when(tmdbClient.getMovieDetail("157336"))
                    .thenReturn(item);
            when(item.title()).thenReturn("인터스텔라");
            when(contentRepository.saveAndFlush(any(Content.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate"));

            ContentDto result =
                    contentService.importExternalContent(ADMIN_EMAIL, request);

            assertThat(result).isSameAs(contentDto);
            verify(contentRepository, times(2))
                    .findBySourceTypeAndExternalId(
                            TMDB_SOURCE_TYPE,
                            "157336"
                    );
        }

        @Test
        @DisplayName("동시 저장 충돌 후 기존 콘텐츠가 없으면 예외를 다시 던진다")
        void importMovie_rethrowsDuplicateWhenExistingMissing() {
            ContentImportRequest request =
                    new ContentImportRequest("157336", ContentType.MOVIE);
            TmdbContentItem item =
                    mock(TmdbContentItem.class);
            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException("duplicate");

            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findBySourceTypeAndExternalId(
                    TMDB_SOURCE_TYPE,
                    "157336"
            )).thenReturn(Optional.empty(), Optional.empty());
            when(tmdbClient.getMovieDetail("157336"))
                    .thenReturn(item);
            when(item.title()).thenReturn("인터스텔라");
            when(contentRepository.saveAndFlush(any(Content.class)))
                    .thenThrow(exception);

            assertThatThrownBy(
                    () -> contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            ).isSameAs(exception);
        }

        @Test
        @DisplayName("일반 사용자는 외부 콘텐츠를 가져올 수 없다")
        void importExternalContent_failsWhenNotAdmin() {
            ContentImportRequest request =
                    new ContentImportRequest("157336", ContentType.MOVIE);

            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(user));

            assertThatThrownBy(
                    () -> contentService.importExternalContent(
                            USER_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("관리자만 콘텐츠를 등록할 수 있습니다.");

            verify(contentRepository, never())
                    .findBySourceTypeAndExternalId(any(), any());
        }
    }

    @Nested
    @DisplayName("콘텐츠 단건 조회")
    class GetContentTest {

        @Test
        @DisplayName("엔티티 통계값으로 상세 DTO를 생성한다")
        void getContent_success() {
            UUID contentId = UUID.randomUUID();

            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));
            when(content.getAverageRating()).thenReturn(4.5);
            when(content.getReviewCount()).thenReturn(10);
            when(content.getWatcherCount()).thenReturn(30L);
            when(contentMapper.toDto(content, 4.5, 10, 30L))
                    .thenReturn(contentDto);

            ContentDto result =
                    contentService.getContent(contentId);

            assertThat(result).isSameAs(contentDto);
            verify(contentMapper).toDto(content, 4.5, 10, 30L);
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠 조회는 실패한다")
        void getContent_failsWhenMissing() {
            UUID contentId = UUID.randomUUID();

            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(
                    () -> contentService.getContent(contentId)
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("존재하지 않는 콘텐츠입니다.");

            verify(contentMapper, never()).toDto(
                    any(),
                    anyDouble(),
                    anyInt(),
                    anyLong()
            );
        }
    }

    @Nested
    @DisplayName("콘텐츠 수정")
    class UpdateContentTest {

        @Test
        @DisplayName("소유자는 콘텐츠를 수정할 수 있다")
        void updateContent_successWhenOwner() {
            UUID ownerId = UUID.randomUUID();
            UUID contentId = UUID.randomUUID();
            ContentUpdateRequest request =
                    mock(ContentUpdateRequest.class);

            when(user.getId()).thenReturn(ownerId);
            when(content.getCreator()).thenReturn(user);
            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(user));
            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));
            when(request.title()).thenReturn("수정 제목");
            when(request.description()).thenReturn("수정 설명");
            when(request.tags()).thenReturn(List.of("수정"));

            ContentDto result =
                    contentService.updateContent(
                            USER_EMAIL,
                            contentId,
                            request
                    );

            assertThat(result).isSameAs(contentDto);
            verify(content).update(
                    "수정 제목",
                    "수정 설명",
                    List.of("수정")
            );
            verify(eventPublisher)
                    .publishEvent(any(ContentEvent.class));
        }

        @Test
        @DisplayName("관리자는 다른 사용자의 콘텐츠를 수정할 수 있다")
        void updateContent_successWhenAdmin() {
            UUID contentId = UUID.randomUUID();
            ContentUpdateRequest request =
                    mock(ContentUpdateRequest.class);

            when(admin.getId()).thenReturn(UUID.randomUUID());
            when(user.getId()).thenReturn(UUID.randomUUID());
            when(content.getCreator()).thenReturn(user);
            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));

            contentService.updateContent(
                    ADMIN_EMAIL,
                    contentId,
                    request
            );

            verify(content).update(
                    request.title(),
                    request.description(),
                    request.tags()
            );
        }

        @Test
        @DisplayName("소유자도 관리자도 아니면 수정에 실패한다")
        void updateContent_failsWithoutPermission() {
            UUID contentId = UUID.randomUUID();
            ContentUpdateRequest request =
                    mock(ContentUpdateRequest.class);

            when(user.getId()).thenReturn(UUID.randomUUID());
            when(otherUser.getId()).thenReturn(UUID.randomUUID());
            when(content.getCreator()).thenReturn(user);
            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(otherUser));
            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));

            assertThatThrownBy(
                    () -> contentService.updateContent(
                            USER_EMAIL,
                            contentId,
                            request
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "콘텐츠를 수정하거나 삭제할 권한이 없습니다."
                    );

            verify(content, never()).update(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("콘텐츠 삭제")
    class DeleteContentTest {

        @Test
        @DisplayName("소유자는 콘텐츠를 삭제할 수 있다")
        void deleteContent_successWhenOwner() {
            UUID ownerId = UUID.randomUUID();
            UUID contentId = UUID.randomUUID();

            when(user.getId()).thenReturn(ownerId);
            when(content.getCreator()).thenReturn(user);
            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(user));
            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));

            contentService.deleteContent(USER_EMAIL, contentId);

            verify(contentRepository).delete(content);
            verify(eventPublisher)
                    .publishEvent(any(ContentEvent.class));
        }

        @Test
        @DisplayName("관리자는 다른 사용자의 콘텐츠를 삭제할 수 있다")
        void deleteContent_successWhenAdmin() {
            UUID contentId = UUID.randomUUID();

            when(admin.getId()).thenReturn(UUID.randomUUID());
            when(user.getId()).thenReturn(UUID.randomUUID());
            when(content.getCreator()).thenReturn(user);
            when(userRepository.findByEmail(ADMIN_EMAIL))
                    .thenReturn(Optional.of(admin));
            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));

            contentService.deleteContent(ADMIN_EMAIL, contentId);

            verify(contentRepository).delete(content);
        }

        @Test
        @DisplayName("권한이 없으면 삭제에 실패한다")
        void deleteContent_failsWithoutPermission() {
            UUID contentId = UUID.randomUUID();

            when(user.getId()).thenReturn(UUID.randomUUID());
            when(otherUser.getId()).thenReturn(UUID.randomUUID());
            when(content.getCreator()).thenReturn(user);
            when(userRepository.findByEmail(USER_EMAIL))
                    .thenReturn(Optional.of(otherUser));
            when(contentRepository.findById(contentId))
                    .thenReturn(Optional.of(content));

            assertThatThrownBy(
                    () -> contentService.deleteContent(
                            USER_EMAIL,
                            contentId
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "콘텐츠를 수정하거나 삭제할 권한이 없습니다."
                    );

            verify(contentRepository, never())
                    .delete(content);
        }
    }

    @Nested
    @DisplayName("콘텐츠 목록 조회")
    class GetContentsTest {

        @Test
        @DisplayName("첫 페이지를 QueryDSL로 조회한다")
        void getContents_firstPageSuccess() {
            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    ContentType.MOVIE,
                    20,
                    "createdAt",
                    Direction.DESCENDING
            )).thenReturn(List.of());
            when(contentRepository.countContents(
                    null,
                    ContentType.MOVIE
            )).thenReturn(0L);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            null,
                            ContentType.MOVIE,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentRepository).findContents(
                    null,
                    null,
                    null,
                    ContentType.MOVIE,
                    20,
                    "createdAt",
                    Direction.DESCENDING
            );
        }

        @Test
        @DisplayName("createdAt 정렬에서 다음 페이지 커서를 생성한다")
        void getContents_createdAtHasNext() {
            Content first = mock(Content.class);
            Content second = mock(Content.class);
            Content extra = mock(Content.class);

            UUID secondId = UUID.randomUUID();
            Instant secondCreatedAt =
                    Instant.parse("2026-07-15T01:00:00Z");

            when(first.getId()).thenReturn(UUID.randomUUID());
            when(second.getId()).thenReturn(secondId);
            when(extra.getId()).thenReturn(UUID.randomUUID());
            when(second.getCreatedAt()).thenReturn(secondCreatedAt);

            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    2,
                    "createdAt",
                    Direction.DESCENDING
            )).thenReturn(List.of(
                    new ContentQueryRow(first, 4.0, 1, 10L),
                    new ContentQueryRow(second, 3.5, 2, 5L),
                    new ContentQueryRow(extra, 3.0, 3, 1L)
            ));
            when(contentRepository.countContents(null, null))
                    .thenReturn(3L);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            2,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentMapper).toSummary(
                    second,
                    3.5,
                    2,
                    5L
            );
        }

        @Test
        @DisplayName("watcherCount 정렬에서 다음 페이지 커서를 생성한다")
        void getContents_watcherCountHasNext() {
            Content first = mock(Content.class);
            Content second = mock(Content.class);
            Content extra = mock(Content.class);

            when(first.getId()).thenReturn(UUID.randomUUID());
            when(second.getId()).thenReturn(UUID.randomUUID());
            when(extra.getId()).thenReturn(UUID.randomUUID());

            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    2,
                    "watcherCount",
                    Direction.DESCENDING
            )).thenReturn(List.of(
                    new ContentQueryRow(first, 4.0, 1, 10L),
                    new ContentQueryRow(second, 3.5, 2, 5L),
                    new ContentQueryRow(extra, 3.0, 3, 1L)
            ));
            when(contentRepository.countContents(null, null))
                    .thenReturn(3L);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            2,
                            "watcherCount",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentMapper).toSummary(
                    second,
                    3.5,
                    2,
                    5L
            );
        }

        @Test
        @DisplayName("rate 정렬에서 다음 페이지 커서를 생성한다")
        void getContents_rateHasNext() {
            Content first = mock(Content.class);
            Content second = mock(Content.class);
            Content extra = mock(Content.class);

            when(first.getId()).thenReturn(UUID.randomUUID());
            when(second.getId()).thenReturn(UUID.randomUUID());
            when(extra.getId()).thenReturn(UUID.randomUUID());

            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    2,
                    "rate",
                    Direction.DESCENDING
            )).thenReturn(List.of(
                    new ContentQueryRow(first, 4.8, 10, 10L),
                    new ContentQueryRow(second, 4.5, 8, 5L),
                    new ContentQueryRow(extra, 4.0, 3, 1L)
            ));
            when(contentRepository.countContents(null, null))
                    .thenReturn(3L);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            2,
                            "rate",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentMapper).toSummary(
                    second,
                    4.5,
                    8,
                    5L
            );
        }

        @Test
        @DisplayName("커서와 UUID idAfter를 QueryDSL에 전달한다")
        void getContents_withCursorSuccess() {
            UUID idAfter = UUID.randomUUID();

            when(contentRepository.findContents(
                    "10",
                    idAfter,
                    null,
                    null,
                    10,
                    "watcherCount",
                    Direction.ASCENDING
            )).thenReturn(List.of());
            when(contentRepository.countContents(null, null))
                    .thenReturn(0L);

            contentService.getContents(
                    "10",
                    idAfter.toString(),
                    null,
                    null,
                    10,
                    "watcherCount",
                    Direction.ASCENDING
            );

            verify(contentRepository).findContents(
                    "10",
                    idAfter,
                    null,
                    null,
                    10,
                    "watcherCount",
                    Direction.ASCENDING
            );
        }

        @Test
        @DisplayName("cursor만 전달하면 실패한다")
        void getContents_failsWhenOnlyCursor() {
            assertThatThrownBy(
                    () -> contentService.getContents(
                            "10",
                            null,
                            null,
                            null,
                            20,
                            "watcherCount",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다."
                    );
        }

        @Test
        @DisplayName("idAfter만 전달하면 실패한다")
        void getContents_failsWhenOnlyIdAfter() {
            assertThatThrownBy(
                    () -> contentService.getContents(
                            null,
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다."
                    );
        }

        @Test
        @DisplayName("지원하지 않는 정렬 기준이면 실패한다")
        void getContents_failsWhenSortInvalid() {
            assertThatThrownBy(
                    () -> contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            20,
                            "title",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "sortBy는 createdAt, watcherCount, rate만 사용할 수 있습니다."
                    );
        }

        @Test
        @DisplayName("limit이 1보다 작으면 실패한다")
        void getContents_failsWhenLimitInvalid() {
            assertThatThrownBy(
                    () -> contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            0,
                            "createdAt",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit은 1 이상이어야 합니다.");
        }

        @Test
        @DisplayName("idAfter가 UUID 형식이 아니면 실패한다")
        void getContents_failsWhenIdAfterInvalid() {
            assertThatThrownBy(
                    () -> contentService.getContents(
                            "10",
                            "invalid-uuid",
                            null,
                            null,
                            20,
                            "watcherCount",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(
                            "idAfter는 올바른 UUID 형식이어야 합니다."
                    );
        }

        @Test
        @DisplayName("일반 검색어는 Elasticsearch를 사용한다")
        void getContents_elasticsearchKeywordSuccess() {
            UUID contentId = UUID.randomUUID();
            ContentDocument document =
                    mock(ContentDocument.class);
            Page<ContentDocument> searchPage =
                    new PageImpl<>(List.of(document));
            Page<Content> contentPage =
                    new PageImpl<>(List.of(content));

            when(document.getId()).thenReturn(contentId.toString());
            when(contentSearchService.search(
                    eq("영화"),
                    any(Pageable.class)
            )).thenReturn(searchPage);
            when(contentRepository.findAll(
                    any(Specification.class),
                    any(Pageable.class)
            )).thenReturn(contentPage);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "영화",
                            null,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentSearchService).search(
                    eq("영화"),
                    any(Pageable.class)
            );
            verify(contentMapper).toSummary(
                    content,
                    0.0,
                    0,
                    0L
            );
        }

        @Test
        @DisplayName("초성 검색어는 초성 검색을 사용한다")
        void getContents_elasticsearchChosungSuccess() {
            UUID contentId = UUID.randomUUID();
            ContentDocument document =
                    mock(ContentDocument.class);

            when(document.getId()).thenReturn(contentId.toString());
            when(contentSearchService.search(
                    eq("ㅇㅌㅅㅌㄹ"),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(document)));
            when(contentRepository.findAll(
                    any(Specification.class),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(content)));

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "ㅇㅌㅅㅌㄹ",
                            null,
                            20,
                            "watcherCount",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentSearchService).search(
                    eq("ㅇㅌㅅㅌㄹ"),
                    any(Pageable.class)
            );
        }

        @Test
        @DisplayName("Elasticsearch 결과가 비어 있으면 빈 응답을 반환한다")
        void getContents_elasticsearchEmpty() {
            when(contentSearchService.search(
                    eq("없음"),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of()));

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "없음",
                            null,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentRepository, never()).findAll(
                    any(Specification.class),
                    any(Pageable.class)
            );
        }

        @Test
        @DisplayName("Elasticsearch 장애 시 DB 검색으로 폴백한다")
        void getContents_elasticsearchFailureFallsBackToDatabase() {
            when(contentSearchService.search(
                    eq("영화"),
                    any(Pageable.class)
            )).thenThrow(new RuntimeException("ES unavailable"));

            when(contentRepository.findContents(
                    null,
                    null,
                    "영화",
                    ContentType.MOVIE,
                    20,
                    "createdAt",
                    Direction.DESCENDING
            )).thenReturn(List.of());
            when(contentRepository.countContents(
                    "영화",
                    ContentType.MOVIE
            )).thenReturn(0L);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "영화",
                            ContentType.MOVIE,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentRepository).findContents(
                    null,
                    null,
                    "영화",
                    ContentType.MOVIE,
                    20,
                    "createdAt",
                    Direction.DESCENDING
            );
        }

        @Test
        @DisplayName("Elasticsearch 타입 필터가 있으면 일치 건수를 조회한다")
        void getContents_elasticsearchTypeCount() {
            UUID contentId = UUID.randomUUID();
            ContentDocument document =
                    mock(ContentDocument.class);

            when(document.getId()).thenReturn(contentId.toString());
            when(contentSearchService.search(
                    eq("영화"),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(document)));
            when(contentRepository.findAll(
                    any(Specification.class),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(content)));
            when(contentRepository.count(any(Specification.class)))
                    .thenReturn(1L);

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "영화",
                            ContentType.MOVIE,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentRepository)
                    .count(any(Specification.class));
        }

        @Test
        @DisplayName("Elasticsearch 다음 페이지의 createdAt 커서를 생성한다")
        void getContents_elasticsearchCreatedAtHasNext() {
            UUID documentId = UUID.randomUUID();
            UUID firstId = UUID.randomUUID();
            UUID secondId = UUID.randomUUID();
            UUID extraId = UUID.randomUUID();

            ContentDocument document =
                    mock(ContentDocument.class);
            Content first = mock(Content.class);
            Content second = mock(Content.class);
            Content extra = mock(Content.class);

            when(document.getId()).thenReturn(documentId.toString());
            when(first.getId()).thenReturn(firstId);
            when(second.getId()).thenReturn(secondId);
            when(extra.getId()).thenReturn(extraId);
            when(second.getCreatedAt())
                    .thenReturn(Instant.parse("2026-07-20T00:00:00Z"));

            when(first.getAverageRating()).thenReturn(4.8);
            when(first.getReviewCount()).thenReturn(1);
            when(first.getWatcherCount()).thenReturn(10L);
            when(second.getAverageRating()).thenReturn(4.5);
            when(second.getReviewCount()).thenReturn(2);
            when(second.getWatcherCount()).thenReturn(5L);

            when(contentSearchService.search(
                    eq("영화"),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(document)));
            when(contentRepository.findAll(
                    any(Specification.class),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(first, second, extra)));

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "영화",
                            null,
                            2,
                            "createdAt",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentMapper).toSummary(
                    second,
                    4.5,
                    2,
                    5L
            );
        }

        @Test
        @DisplayName("Elasticsearch 다음 페이지의 watcherCount 커서를 생성한다")
        void getContents_elasticsearchWatcherCountHasNext() {
            UUID documentId = UUID.randomUUID();
            ContentDocument document =
                    mock(ContentDocument.class);
            Content first = mock(Content.class);
            Content second = mock(Content.class);
            Content extra = mock(Content.class);

            when(document.getId()).thenReturn(documentId.toString());
            when(first.getId()).thenReturn(UUID.randomUUID());
            when(second.getId()).thenReturn(UUID.randomUUID());
            when(extra.getId()).thenReturn(UUID.randomUUID());

            when(first.getAverageRating()).thenReturn(4.8);
            when(first.getReviewCount()).thenReturn(1);
            when(first.getWatcherCount()).thenReturn(10L);
            when(second.getAverageRating()).thenReturn(4.5);
            when(second.getReviewCount()).thenReturn(2);
            when(second.getWatcherCount()).thenReturn(5L);

            when(contentSearchService.search(
                    eq("영화"),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(document)));
            when(contentRepository.findAll(
                    any(Specification.class),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(first, second, extra)));

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "영화",
                            null,
                            2,
                            "watcherCount",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentMapper).toSummary(
                    second,
                    4.5,
                    2,
                    5L
            );
        }

        @Test
        @DisplayName("Elasticsearch 다음 페이지의 rate 커서를 생성한다")
        void getContents_elasticsearchRateHasNext() {
            UUID documentId = UUID.randomUUID();
            ContentDocument document =
                    mock(ContentDocument.class);
            Content first = mock(Content.class);
            Content second = mock(Content.class);
            Content extra = mock(Content.class);

            when(document.getId()).thenReturn(documentId.toString());
            when(first.getId()).thenReturn(UUID.randomUUID());
            when(second.getId()).thenReturn(UUID.randomUUID());
            when(extra.getId()).thenReturn(UUID.randomUUID());

            when(first.getAverageRating()).thenReturn(4.8);
            when(first.getReviewCount()).thenReturn(1);
            when(first.getWatcherCount()).thenReturn(10L);
            when(second.getAverageRating()).thenReturn(4.5);
            when(second.getReviewCount()).thenReturn(2);
            when(second.getWatcherCount()).thenReturn(5L);

            when(contentSearchService.search(
                    eq("영화"),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(document)));
            when(contentRepository.findAll(
                    any(Specification.class),
                    any(Pageable.class)
            )).thenReturn(new PageImpl<>(List.of(first, second, extra)));

            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            "영화",
                            null,
                            2,
                            "rate",
                            Direction.DESCENDING
                    );

            assertThat(result).isNotNull();
            verify(contentMapper).toSummary(
                    second,
                    4.5,
                    2,
                    5L
            );
        }
    }

    @Nested
    @DisplayName("외부 콘텐츠 검색")
    class SearchExternalContentsTest {

        @Test
        @DisplayName("영화 검색 결과를 변환한다")
        void searchExternalContents_movieSuccess() {
            TmdbSearchResponse response =
                    mock(TmdbSearchResponse.class);
            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            when(tmdbClient.searchMovies("interstellar"))
                    .thenReturn(response);
            when(response.results()).thenReturn(List.of(item));
            when(item.id()).thenReturn(157336L);
            when(item.title()).thenReturn("Interstellar");
            when(item.overview()).thenReturn("우주 탐사 영화");
            when(item.poster_path()).thenReturn("/poster.jpg");
            when(item.release_date()).thenReturn("2014-11-05");

            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "interstellar",
                            ContentType.MOVIE
                    );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).externalId())
                    .isEqualTo("157336");
            assertThat(results.get(0).type())
                    .isEqualTo(ContentType.MOVIE);
            assertThat(results.get(0).title())
                    .isEqualTo("Interstellar");
            assertThat(results.get(0).thumbnailUrl())
                    .isEqualTo(
                            "https://image.tmdb.org/t/p/w500/poster.jpg"
                    );
            assertThat(results.get(0).releaseDate())
                    .isEqualTo("2014-11-05");
        }

        @Test
        @DisplayName("TV 검색 결과를 변환한다")
        void searchExternalContents_tvSuccess() {
            TmdbSearchResponse response =
                    mock(TmdbSearchResponse.class);
            TmdbContentItem item =
                    mock(TmdbContentItem.class);

            when(tmdbClient.searchTvSeries("breaking bad"))
                    .thenReturn(response);
            when(response.results()).thenReturn(List.of(item));
            when(item.id()).thenReturn(1396L);
            when(item.name()).thenReturn("Breaking Bad");
            when(item.overview()).thenReturn("TV 시리즈");
            when(item.poster_path()).thenReturn(null);
            when(item.first_air_date()).thenReturn("2008-01-20");

            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "breaking bad",
                            ContentType.TVSERIES
                    );

            assertThat(results).hasSize(1);
            assertThat(results.get(0).externalId())
                    .isEqualTo("1396");
            assertThat(results.get(0).type())
                    .isEqualTo(ContentType.TVSERIES);
            assertThat(results.get(0).title())
                    .isEqualTo("Breaking Bad");
            assertThat(results.get(0).thumbnailUrl())
                    .isNull();
            assertThat(results.get(0).releaseDate())
                    .isEqualTo("2008-01-20");
        }

        @Test
        @DisplayName("TMDB 응답이 null이면 빈 목록을 반환한다")
        void searchExternalContents_returnsEmptyWhenResponseNull() {
            when(tmdbClient.searchMovies("없는 영화"))
                    .thenReturn(null);

            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "없는 영화",
                            ContentType.MOVIE
                    );

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("TMDB 결과 목록이 null이면 빈 목록을 반환한다")
        void searchExternalContents_returnsEmptyWhenResultsNull() {
            TmdbSearchResponse response =
                    mock(TmdbSearchResponse.class);

            when(tmdbClient.searchMovies("없는 영화"))
                    .thenReturn(response);
            when(response.results()).thenReturn(null);

            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "없는 영화",
                            ContentType.MOVIE
                    );

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("스포츠 팀 응답이 null이면 빈 목록을 반환한다")
        void searchExternalContents_sportReturnsEmptyWhenResponseNull() {
            when(sportsDbClient.searchTeams("서울"))
                    .thenReturn(null);

            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "서울",
                            ContentType.SPORT
                    );

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("스포츠 팀 목록이 null이면 빈 목록을 반환한다")
        void searchExternalContents_sportReturnsEmptyWhenTeamsNull() {
            SportsDbTeamResponse response =
                    mock(SportsDbTeamResponse.class);

            when(sportsDbClient.searchTeams("서울"))
                    .thenReturn(response);
            when(response.teams()).thenReturn(null);

            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "서울",
                            ContentType.SPORT
                    );

            assertThat(results).isEmpty();
        }
    }
}