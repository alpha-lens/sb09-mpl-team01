package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.client.SportsDbClient;
import com.codeit.mpl.domain.content.client.TmdbClient;
import com.codeit.mpl.domain.content.client.TmdbProperties;
import com.codeit.mpl.domain.content.dto.external.TmdbContentItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventItem;
import com.codeit.mpl.domain.content.dto.external.SportsDbEventResponse;
import com.codeit.mpl.domain.content.dto.query.ContentQueryRow;
import com.codeit.mpl.domain.content.dto.external.TmdbSearchResponse;
import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentSourceType;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.mapper.ContentMapper;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.WatchingSessionRepository;
import com.codeit.mpl.domain.review.repository.ReviewRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContentService 단위 테스트")
class ContentServiceTest {

    private static final String ADMIN_EMAIL =
            "admin@mopl.io";

    private static final String USER_EMAIL =
            "user@mopl.io";

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private WatchingSessionRepository watchingSessionRepository;

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private SportsDbClient sportsDbClient;

    @Mock
    private TmdbProperties tmdbProperties;

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
        contentService =
                new ContentService(
                        contentRepository,
                        userRepository,
                        contentMapper,
                        reviewRepository,
                        watchingSessionRepository,
                        tmdbClient,
                        sportsDbClient,
                        tmdbProperties
                );

        when(admin.getRole())
                .thenReturn(UserRole.ADMIN);

        when(user.getRole())
                .thenReturn(UserRole.USER);

        when(otherUser.getRole())
                .thenReturn(UserRole.USER);

        when(reviewRepository.findAverageRatingByContent(
                any(Content.class)
        )).thenReturn(0.0);

        when(reviewRepository.countByContent(
                any(Content.class)
        )).thenReturn(0L);

        when(watchingSessionRepository.countByContent(
                any(Content.class)
        )).thenReturn(0L);

        when(contentMapper.toDto(
                any(Content.class),
                anyDouble(),
                anyInt(),
                anyLong()
        )).thenReturn(contentDto);
    }

    @Nested
    @DisplayName("콘텐츠 직접 생성")
    class CreateContentTest {

        @Test
        @DisplayName("관리자는 콘텐츠를 직접 생성할 수 있다")
        void createContent_success() {
            // given
            ContentCreateRequest request =
                    org.mockito.Mockito.mock(
                            ContentCreateRequest.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(request.type())
                    .thenReturn(ContentType.MOVIE);

            when(request.title())
                    .thenReturn("인터스텔라");

            when(request.description())
                    .thenReturn("우주 탐사 영화");

            when(request.tags())
                    .thenReturn(
                            List.of(
                                    "SF",
                                    "MOVIE"
                            )
                    );

            when(contentRepository.save(
                    any(Content.class)
            )).thenAnswer(
                    invocation ->
                            invocation.getArgument(0)
            );

            // when
            ContentDto result =
                    contentService.createContent(
                            ADMIN_EMAIL,
                            request
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(userRepository)
                    .findByEmail(ADMIN_EMAIL);

            verify(contentRepository)
                    .save(any(Content.class));

            verify(contentMapper)
                    .toDto(
                            any(Content.class),
                            eq(0.0),
                            eq(0),
                            eq(0L)
                    );
        }

        @Test
        @DisplayName("일반 사용자는 콘텐츠를 직접 생성할 수 없다")
        void createContent_failsWhenRequesterIsNotAdmin() {
            // given
            ContentCreateRequest request =
                    org.mockito.Mockito.mock(
                            ContentCreateRequest.class
                    );

            when(userRepository.findByEmail(
                    USER_EMAIL
            )).thenReturn(
                    Optional.of(user)
            );

            // when & then
            assertThatThrownBy(() ->
                    contentService.createContent(
                            USER_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "관리자만 콘텐츠를 등록할 수 있습니다."
                    );

            verify(contentRepository, never())
                    .save(any(Content.class));
        }

        @Test
        @DisplayName("요청자 이메일이 null이면 생성에 실패한다")
        void createContent_failsWhenEmailIsNull() {
            // given
            ContentCreateRequest request =
                    org.mockito.Mockito.mock(
                            ContentCreateRequest.class
                    );

            // when & then
            assertThatThrownBy(() ->
                    contentService.createContent(
                            null,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "인증 정보가 유효하지 않습니다."
                    );

            verify(userRepository, never())
                    .findByEmail(any());
        }

        @Test
        @DisplayName("요청자 이메일이 공백이면 생성에 실패한다")
        void createContent_failsWhenEmailIsBlank() {
            // given
            ContentCreateRequest request =
                    org.mockito.Mockito.mock(
                            ContentCreateRequest.class
                    );

            // when & then
            assertThatThrownBy(() ->
                    contentService.createContent(
                            "   ",
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "인증 정보가 유효하지 않습니다."
                    );

            verify(userRepository, never())
                    .findByEmail(any());
        }

        @Test
        @DisplayName("요청자가 존재하지 않으면 생성에 실패한다")
        void createContent_failsWhenRequesterDoesNotExist() {
            // given
            ContentCreateRequest request =
                    org.mockito.Mockito.mock(
                            ContentCreateRequest.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.empty()
            );

            // when & then
            assertThatThrownBy(() ->
                    contentService.createContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "존재하지 않는 사용자입니다."
                    );

            verify(contentRepository, never())
                    .save(any(Content.class));
        }
    }

    @Nested
    @DisplayName("외부 콘텐츠 가져오기")
    class ImportExternalContentTest {

        @Test
        @DisplayName("이미 저장된 TMDB 영화가 있으면 새로 저장하지 않고 기존 콘텐츠를 반환한다")
        void importMovie_returnsExistingContent() {
            // given
            ContentImportRequest request =
                    org.mockito.Mockito.mock(
                            ContentImportRequest.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(request.type())
                    .thenReturn(ContentType.MOVIE);

            when(request.externalId())
                    .thenReturn("157336");

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "157336"
                    ))
                    .thenReturn(
                            Optional.of(content)
                    );

            // when
            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(contentRepository)
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "157336"
                    );

            verify(tmdbClient, never())
                    .getMovieDetail(any());

            verify(contentRepository, never())
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("이미 저장된 TMDB TV 시리즈가 있으면 기존 콘텐츠를 반환한다")
        void importTvSeries_returnsExistingContent() {
            // given
            ContentImportRequest request =
                    org.mockito.Mockito.mock(
                            ContentImportRequest.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(request.type())
                    .thenReturn(ContentType.TVSERIES);

            when(request.externalId())
                    .thenReturn("1399");

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_TV,
                            "1399"
                    ))
                    .thenReturn(
                            Optional.of(content)
                    );

            // when
            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(contentRepository)
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_TV,
                            "1399"
                    );

            verify(tmdbClient, never())
                    .getTvSeriesDetail(any());
        }

        @Test
        @DisplayName("이미 저장된 스포츠 경기가 있으면 기존 콘텐츠를 반환한다")
        void importSport_returnsExistingContent() {
            // given
            ContentImportRequest request =
                    org.mockito.Mockito.mock(
                            ContentImportRequest.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(request.type())
                    .thenReturn(ContentType.SPORT);

            when(request.externalId())
                    .thenReturn("12345");

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.THE_SPORTS_DB,
                            "12345"
                    ))
                    .thenReturn(
                            Optional.of(content)
                    );

            // when
            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(contentRepository)
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.THE_SPORTS_DB,
                            "12345"
                    );

            verify(sportsDbClient, never())
                    .getEventDetail(any());
        }

        @Test
        @DisplayName("TMDB 영화 상세 결과가 null이면 가져오기에 실패한다")
        void importMovie_failsWhenTmdbResponseIsNull() {
            // given
            ContentImportRequest request =
                    org.mockito.Mockito.mock(
                            ContentImportRequest.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(request.type())
                    .thenReturn(ContentType.MOVIE);

            when(request.externalId())
                    .thenReturn("999999");

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "999999"
                    ))
                    .thenReturn(
                            Optional.empty()
                    );

            when(tmdbClient.getMovieDetail(
                    "999999"
            )).thenReturn(null);

            // when & then
            assertThatThrownBy(() ->
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "존재하지 않는 TMDB 콘텐츠입니다."
                    );

            verify(contentRepository, never())
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("TMDB 상세 결과에 ID가 없으면 가져오기에 실패한다")
        void importMovie_failsWhenTmdbItemIdIsNull() {
            // given
            ContentImportRequest request =
                    org.mockito.Mockito.mock(
                            ContentImportRequest.class
                    );

            TmdbContentItem item =
                    org.mockito.Mockito.mock(
                            TmdbContentItem.class
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(request.type())
                    .thenReturn(ContentType.MOVIE);

            when(request.externalId())
                    .thenReturn("999999");

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "999999"
                    ))
                    .thenReturn(
                            Optional.empty()
                    );

            when(tmdbClient.getMovieDetail(
                    "999999"
            )).thenReturn(item);

            when(item.id())
                    .thenReturn(null);

            // when & then
            assertThatThrownBy(() ->
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "존재하지 않는 TMDB 콘텐츠입니다."
                    );

            verify(contentRepository, never())
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("일반 사용자는 외부 콘텐츠를 가져올 수 없다")
        void importExternalContent_failsWhenRequesterIsNotAdmin() {
            // given
            ContentImportRequest request =
                    org.mockito.Mockito.mock(
                            ContentImportRequest.class
                    );

            when(userRepository.findByEmail(
                    USER_EMAIL
            )).thenReturn(
                    Optional.of(user)
            );

            // when & then
            assertThatThrownBy(() ->
                    contentService.importExternalContent(
                            USER_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "관리자만 콘텐츠를 등록할 수 있습니다."
                    );

            verify(contentRepository, never())
                    .findBySourceTypeAndExternalId(
                            any(),
                            any()
                    );
        }
    }

    @Nested
    @DisplayName("콘텐츠 단건 조회")
    class GetContentTest {

        @Test
        @DisplayName("콘텐츠를 정상적으로 조회한다")
        void getContent_success() {
            // given
            UUID contentId =
                    UUID.randomUUID();

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            when(reviewRepository
                    .findAverageRatingByContent(
                            content
                    ))
                    .thenReturn(4.5);

            when(reviewRepository.countByContent(
                    content
            )).thenReturn(10L);

            when(watchingSessionRepository
                    .countByContent(
                            content
                    ))
                    .thenReturn(3L);

            when(contentMapper.toDto(
                    content,
                    4.5,
                    10,
                    3L
            )).thenReturn(contentDto);

            // when
            ContentDto result =
                    contentService.getContent(
                            contentId
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(contentRepository)
                    .findById(contentId);

            verify(contentMapper)
                    .toDto(
                            content,
                            4.5,
                            10,
                            3L
                    );
        }

        @Test
        @DisplayName("평균 평점이 null이면 0점으로 변환한다")
        void getContent_convertsNullAverageRatingToZero() {
            // given
            UUID contentId =
                    UUID.randomUUID();

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            when(reviewRepository
                    .findAverageRatingByContent(
                            content
                    ))
                    .thenReturn(null);

            when(reviewRepository.countByContent(
                    content
            )).thenReturn(0L);

            when(watchingSessionRepository
                    .countByContent(
                            content
                    ))
                    .thenReturn(0L);

            when(contentMapper.toDto(
                    content,
                    0.0,
                    0,
                    0L
            )).thenReturn(contentDto);

            // when
            ContentDto result =
                    contentService.getContent(
                            contentId
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(contentMapper)
                    .toDto(
                            content,
                            0.0,
                            0,
                            0L
                    );
        }

        @Test
        @DisplayName("존재하지 않는 콘텐츠를 조회하면 실패한다")
        void getContent_failsWhenContentDoesNotExist() {
            // given
            UUID contentId =
                    UUID.randomUUID();

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.empty()
            );

            // when & then
            assertThatThrownBy(() ->
                    contentService.getContent(
                            contentId
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "존재하지 않는 콘텐츠입니다."
                    );

            verify(contentMapper, never())
                    .toDto(
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
        @DisplayName("콘텐츠 소유자는 콘텐츠를 수정할 수 있다")
        void updateContent_successWhenRequesterIsOwner() {
            // given
            UUID requesterId =
                    UUID.randomUUID();

            UUID contentId =
                    UUID.randomUUID();

            ContentUpdateRequest request =
                    org.mockito.Mockito.mock(
                            ContentUpdateRequest.class
                    );

            when(user.getId())
                    .thenReturn(requesterId);

            when(content.getCreator())
                    .thenReturn(user);

            when(userRepository.findByEmail(
                    USER_EMAIL
            )).thenReturn(
                    Optional.of(user)
            );

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            when(request.title())
                    .thenReturn("수정된 제목");

            when(request.description())
                    .thenReturn("수정된 설명");

            when(request.tags())
                    .thenReturn(
                            List.of(
                                    "수정",
                                    "MOVIE"
                            )
                    );

            // when
            ContentDto result =
                    contentService.updateContent(
                            USER_EMAIL,
                            contentId,
                            request
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(content)
                    .update(
                            "수정된 제목",
                            "수정된 설명",
                            List.of(
                                    "수정",
                                    "MOVIE"
                            )
                    );
        }

        @Test
        @DisplayName("관리자는 다른 사용자의 콘텐츠를 수정할 수 있다")
        void updateContent_successWhenRequesterIsAdmin() {
            // given
            UUID adminId =
                    UUID.randomUUID();

            UUID ownerId =
                    UUID.randomUUID();

            UUID contentId =
                    UUID.randomUUID();

            ContentUpdateRequest request =
                    org.mockito.Mockito.mock(
                            ContentUpdateRequest.class
                    );

            when(admin.getId())
                    .thenReturn(adminId);

            when(user.getId())
                    .thenReturn(ownerId);

            when(content.getCreator())
                    .thenReturn(user);

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            when(request.title())
                    .thenReturn("관리자 수정");

            when(request.description())
                    .thenReturn("관리자 수정 설명");

            when(request.tags())
                    .thenReturn(
                            List.of("ADMIN")
                    );

            // when
            ContentDto result =
                    contentService.updateContent(
                            ADMIN_EMAIL,
                            contentId,
                            request
                    );

            // then
            assertThat(result)
                    .isSameAs(contentDto);

            verify(content)
                    .update(
                            "관리자 수정",
                            "관리자 수정 설명",
                            List.of("ADMIN")
                    );
        }

        @Test
        @DisplayName("소유자도 관리자도 아니면 수정에 실패한다")
        void updateContent_failsWhenRequesterHasNoPermission() {
            // given
            UUID ownerId =
                    UUID.randomUUID();

            UUID requesterId =
                    UUID.randomUUID();

            UUID contentId =
                    UUID.randomUUID();

            ContentUpdateRequest request =
                    org.mockito.Mockito.mock(
                            ContentUpdateRequest.class
                    );

            when(user.getId())
                    .thenReturn(ownerId);

            when(otherUser.getId())
                    .thenReturn(requesterId);

            when(content.getCreator())
                    .thenReturn(user);

            when(userRepository.findByEmail(
                    USER_EMAIL
            )).thenReturn(
                    Optional.of(otherUser)
            );

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            // when & then
            assertThatThrownBy(() ->
                    contentService.updateContent(
                            USER_EMAIL,
                            contentId,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "콘텐츠를 수정하거나 삭제할 권한이 없습니다."
                    );

            verify(content, never())
                    .update(
                            any(),
                            any(),
                            any()
                    );
        }
    }

    @Nested
    @DisplayName("콘텐츠 삭제")
    class DeleteContentTest {

        @Test
        @DisplayName("콘텐츠 소유자는 콘텐츠를 삭제할 수 있다")
        void deleteContent_successWhenRequesterIsOwner() {
            // given
            UUID requesterId =
                    UUID.randomUUID();

            UUID contentId =
                    UUID.randomUUID();

            when(user.getId())
                    .thenReturn(requesterId);

            when(content.getCreator())
                    .thenReturn(user);

            when(userRepository.findByEmail(
                    USER_EMAIL
            )).thenReturn(
                    Optional.of(user)
            );

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            // when
            contentService.deleteContent(
                    USER_EMAIL,
                    contentId
            );

            // then
            verify(contentRepository)
                    .delete(content);
        }

        @Test
        @DisplayName("관리자는 다른 사용자의 콘텐츠를 삭제할 수 있다")
        void deleteContent_successWhenRequesterIsAdmin() {
            // given
            UUID contentId =
                    UUID.randomUUID();

            when(admin.getId())
                    .thenReturn(UUID.randomUUID());

            when(user.getId())
                    .thenReturn(UUID.randomUUID());

            when(content.getCreator())
                    .thenReturn(user);

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            // when
            contentService.deleteContent(
                    ADMIN_EMAIL,
                    contentId
            );

            // then
            verify(contentRepository)
                    .delete(content);
        }

        @Test
        @DisplayName("소유자도 관리자도 아니면 삭제에 실패한다")
        void deleteContent_failsWhenRequesterHasNoPermission() {
            // given
            UUID contentId =
                    UUID.randomUUID();

            when(user.getId())
                    .thenReturn(UUID.randomUUID());

            when(otherUser.getId())
                    .thenReturn(UUID.randomUUID());

            when(content.getCreator())
                    .thenReturn(user);

            when(userRepository.findByEmail(
                    USER_EMAIL
            )).thenReturn(
                    Optional.of(otherUser)
            );

            when(contentRepository.findById(
                    contentId
            )).thenReturn(
                    Optional.of(content)
            );

            // when & then
            assertThatThrownBy(() ->
                    contentService.deleteContent(
                            USER_EMAIL,
                            contentId
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "콘텐츠를 수정하거나 삭제할 권한이 없습니다."
                    );

            verify(contentRepository, never())
                    .delete(content);
        }
    }

    @Nested
    @DisplayName("외부 콘텐츠 검색")
    class SearchExternalContentsTest {

        @Test
        @DisplayName("TMDB 영화 검색 결과를 외부 콘텐츠 응답으로 변환한다")
        void searchExternalContents_movieSuccess() {
            // given
            TmdbSearchResponse response =
                    org.mockito.Mockito.mock(
                            TmdbSearchResponse.class
                    );

            TmdbContentItem item =
                    org.mockito.Mockito.mock(
                            TmdbContentItem.class
                    );

            when(tmdbClient.searchMovies(
                    "interstellar"
            )).thenReturn(response);

            when(response.results())
                    .thenReturn(
                            List.of(item)
                    );

            when(item.id())
                    .thenReturn(157336L);

            when(item.title())
                    .thenReturn("Interstellar");

            when(item.overview())
                    .thenReturn("우주 탐사 영화");

            when(item.poster_path())
                    .thenReturn("/poster.jpg");

            when(item.release_date())
                    .thenReturn("2014-11-05");

            when(tmdbProperties.imageBaseUrl())
                    .thenReturn(
                            "https://image.tmdb.org/t/p/w500"
                    );

            // when
            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "interstellar",
                            ContentType.MOVIE
                    );

            // then
            assertThat(results)
                    .hasSize(1);

            ExternalContentSearchResult result =
                    results.get(0);

            assertThat(result.externalId())
                    .isEqualTo("157336");

            assertThat(result.type())
                    .isEqualTo(ContentType.MOVIE);

            assertThat(result.title())
                    .isEqualTo("Interstellar");

            assertThat(result.description())
                    .isEqualTo("우주 탐사 영화");

            assertThat(result.thumbnailUrl())
                    .isEqualTo(
                            "https://image.tmdb.org/t/p/w500/poster.jpg"
                    );

            assertThat(result.releaseDate())
                    .isEqualTo("2014-11-05");
        }

        @Test
        @DisplayName("TMDB TV 시리즈 검색 결과를 변환한다")
        void searchExternalContents_tvSeriesSuccess() {
            // given
            TmdbSearchResponse response =
                    org.mockito.Mockito.mock(
                            TmdbSearchResponse.class
                    );

            TmdbContentItem item =
                    org.mockito.Mockito.mock(
                            TmdbContentItem.class
                    );

            when(tmdbClient.searchTvSeries(
                    "breaking bad"
            )).thenReturn(response);

            when(response.results())
                    .thenReturn(
                            List.of(item)
                    );

            when(item.id())
                    .thenReturn(1396L);

            when(item.name())
                    .thenReturn("Breaking Bad");

            when(item.overview())
                    .thenReturn("TV 시리즈");

            when(item.poster_path())
                    .thenReturn(null);

            when(item.first_air_date())
                    .thenReturn("2008-01-20");

            // when
            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "breaking bad",
                            ContentType.TVSERIES
                    );

            // then
            assertThat(results)
                    .hasSize(1);

            ExternalContentSearchResult result =
                    results.get(0);

            assertThat(result.externalId())
                    .isEqualTo("1396");

            assertThat(result.type())
                    .isEqualTo(ContentType.TVSERIES);

            assertThat(result.title())
                    .isEqualTo("Breaking Bad");

            assertThat(result.thumbnailUrl())
                    .isNull();

            assertThat(result.releaseDate())
                    .isEqualTo("2008-01-20");
        }

        @Test
        @DisplayName("TMDB 응답이 null이면 빈 목록을 반환한다")
        void searchExternalContents_returnsEmptyWhenResponseIsNull() {
            // given
            when(tmdbClient.searchMovies(
                    "없는 영화"
            )).thenReturn(null);

            // when
            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "없는 영화",
                            ContentType.MOVIE
                    );

            // then
            assertThat(results)
                    .isEmpty();
        }

        @Test
        @DisplayName("TMDB 검색 결과 목록이 null이면 빈 목록을 반환한다")
        void searchExternalContents_returnsEmptyWhenResultsAreNull() {
            // given
            TmdbSearchResponse response =
                    org.mockito.Mockito.mock(
                            TmdbSearchResponse.class
                    );

            when(tmdbClient.searchMovies(
                    "없는 영화"
            )).thenReturn(response);

            when(response.results())
                    .thenReturn(null);

            // when
            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "없는 영화",
                            ContentType.MOVIE
                    );

            // then
            assertThat(results)
                    .isEmpty();
        }

        @Test
        @DisplayName("검색 결과 중 null 항목과 ID가 없는 항목은 제외한다")
        void searchExternalContents_filtersInvalidItems() {
            // given
            TmdbSearchResponse response =
                    org.mockito.Mockito.mock(
                            TmdbSearchResponse.class
                    );

            TmdbContentItem invalidItem =
                    org.mockito.Mockito.mock(
                            TmdbContentItem.class
                    );

            TmdbContentItem validItem =
                    org.mockito.Mockito.mock(
                            TmdbContentItem.class
                    );

            when(tmdbClient.searchMovies(
                    "movie"
            )).thenReturn(response);

            when(response.results())
                    .thenReturn(
                            java.util.Arrays.asList(
                                    null,
                                    invalidItem,
                                    validItem
                            )
                    );

            when(invalidItem.id())
                    .thenReturn(null);

            when(validItem.id())
                    .thenReturn(1L);

            when(validItem.title())
                    .thenReturn("정상 영화");

            when(validItem.poster_path())
                    .thenReturn(null);

            // when
            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(
                            "movie",
                            ContentType.MOVIE
                    );

            // then
            assertThat(results)
                    .hasSize(1);

            assertThat(results.get(0).externalId())
                    .isEqualTo("1");

            assertThat(results.get(0).title())
                    .isEqualTo("정상 영화");
        }
    }

    @Nested
    @DisplayName("콘텐츠 목록 조회 입력 검증")
    class GetContentsValidationTest {

        @Test
        @DisplayName("cursor만 전달하면 목록 조회에 실패한다")
        void getContents_failsWhenOnlyCursorIsProvided() {
            // when & then
            assertThatThrownBy(() ->
                    contentService.getContents(
                            "2026-01-01T00:00:00",
                            null,
                            null,
                            null,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다."
                    );

            verify(contentRepository, never())
                    .findContents(
                            any(),
                            any(),
                            any(),
                            any(),
                            anyInt(),
                            any(),
                            any()
                    );
        }

        @Test
        @DisplayName("idAfter만 전달하면 목록 조회에 실패한다")
        void getContents_failsWhenOnlyIdAfterIsProvided() {
            // when & then
            assertThatThrownBy(() ->
                    contentService.getContents(
                            null,
                            UUID.randomUUID().toString(),
                            null,
                            null,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "cursor와 idAfter는 함께 전달하거나 모두 생략해야 합니다."
                    );
        }

        @Test
        @DisplayName("지원하지 않는 정렬 기준이면 목록 조회에 실패한다")
        void getContents_failsWhenSortByIsInvalid() {
            // when & then
            assertThatThrownBy(() ->
                    contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            20,
                            "title",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "sortBy는 createdAt, watcherCount, rate만 사용할 수 있습니다."
                    );
        }

        @Test
        @DisplayName("idAfter가 UUID 형식이 아니면 목록 조회에 실패한다")
        void getContents_failsWhenIdAfterIsInvalidUuid() {
            // when & then
            assertThatThrownBy(() ->
                    contentService.getContents(
                            "cursor",
                            "invalid-uuid",
                            null,
                            null,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "idAfter는 올바른 UUID 형식이어야 합니다."
                    );
        }

        @Test
        @DisplayName("커서가 없으면 첫 페이지를 정상적으로 조회한다")
        void getContents_firstPageSuccess() {
            // given
            when(contentRepository.findContents(
                    null,
                    null,
                    "영화",
                    ContentType.MOVIE,
                    20,
                    "createdAt",
                    Direction.DESCENDING
            )).thenReturn(
                    List.of()
            );

            when(contentRepository.countContents(
                    "영화",
                    ContentType.MOVIE
            )).thenReturn(0L);

            // when
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

            // then
            assertThat(result)
                    .isNotNull();

            verify(contentRepository)
                    .findContents(
                            null,
                            null,
                            "영화",
                            ContentType.MOVIE,
                            20,
                            "createdAt",
                            Direction.DESCENDING
                    );

            verify(contentRepository)
                    .countContents(
                            "영화",
                            ContentType.MOVIE
                    );
        }

        @Test
        @DisplayName("watcherCount 정렬로 첫 페이지를 조회할 수 있다")
        void getContents_watcherCountSortSuccess() {
            // given
            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    10,
                    "watcherCount",
                    Direction.DESCENDING
            )).thenReturn(
                    List.of()
            );

            when(contentRepository.countContents(
                    null,
                    null
            )).thenReturn(0L);

            // when
            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            10,
                            "watcherCount",
                            Direction.DESCENDING
                    );

            // then
            assertThat(result)
                    .isNotNull();
        }

        @Test
        @DisplayName("rate 정렬로 첫 페이지를 조회할 수 있다")
        void getContents_rateSortSuccess() {
            // given
            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    10,
                    "rate",
                    Direction.DESCENDING
            )).thenReturn(
                    List.of()
            );

            when(contentRepository.countContents(
                    null,
                    null
            )).thenReturn(0L);

            // when
            CursorPageResponseDto<ContentSummary> result =
                    contentService.getContents(
                            null,
                            null,
                            null,
                            null,
                            10,
                            "rate",
                            Direction.DESCENDING
                    );

            // then
            assertThat(result)
                    .isNotNull();
        }
    }

    @Nested
    @DisplayName("외부 콘텐츠 신규 Import")
    class ImportNewExternalContentTest {

        @Test
        @DisplayName("신규 TMDB 영화를 가져와 저장한다")
        void importMovie_success() {
            ContentImportRequest request =
                    new ContentImportRequest(
                            "157336",
                            ContentType.MOVIE
                    );

            TmdbContentItem item =
                    new TmdbContentItem(
                            157336L,
                            "인터스텔라",
                            null,
                            "우주 탐사 영화",
                            "/poster.jpg",
                            null,
                            "2014-11-05",
                            null
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "157336"
                    ))
                    .thenReturn(Optional.empty());

            when(tmdbClient.getMovieDetail(
                    "157336"
            )).thenReturn(item);

            when(tmdbProperties.imageBaseUrl())
                    .thenReturn(
                            "https://image.tmdb.org/t/p/w500"
                    );

            when(contentRepository.saveAndFlush(
                    any(Content.class)
            )).thenAnswer(
                    invocation ->
                            invocation.getArgument(0)
            );

            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            assertThat(result)
                    .isSameAs(contentDto);

            verify(tmdbClient)
                    .getMovieDetail("157336");

            verify(contentRepository)
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("신규 TMDB TV 시리즈를 가져와 저장한다")
        void importTvSeries_success() {
            ContentImportRequest request =
                    new ContentImportRequest(
                            "1396",
                            ContentType.TVSERIES
                    );

            TmdbContentItem item =
                    new TmdbContentItem(
                            1396L,
                            null,
                            "Breaking Bad",
                            "TV 시리즈 설명",
                            "/tv-poster.jpg",
                            null,
                            null,
                            "2008-01-20"
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_TV,
                            "1396"
                    ))
                    .thenReturn(Optional.empty());

            when(tmdbClient.getTvSeriesDetail(
                    "1396"
            )).thenReturn(item);

            when(tmdbProperties.imageBaseUrl())
                    .thenReturn(
                            "https://image.tmdb.org/t/p/w500"
                    );

            when(contentRepository.saveAndFlush(
                    any(Content.class)
            )).thenAnswer(
                    invocation ->
                            invocation.getArgument(0)
            );

            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            assertThat(result)
                    .isSameAs(contentDto);

            verify(tmdbClient)
                    .getTvSeriesDetail("1396");

            verify(contentRepository)
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("신규 스포츠 경기를 가져와 저장한다")
        void importSport_success() {
            ContentImportRequest request =
                    new ContentImportRequest(
                            "12345",
                            ContentType.SPORT
                    );

            SportsDbEventItem event =
                    new SportsDbEventItem(
                            "12345",
                            "서울 vs 부산",
                            "Soccer",
                            "4328",
                            "K League",
                            "https://image/league.png",
                            "2026",
                            "1",
                            "서울",
                            "https://image/home.png",
                            "2",
                            "부산",
                            "https://image/away.png",
                            "2026-07-20",
                            "18:00:00",
                            "서울 경기장",
                            null,
                            null,
                            null,
                            null,
                            null,
                            "경기 설명"
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.THE_SPORTS_DB,
                            "12345"
                    ))
                    .thenReturn(Optional.empty());

            when(sportsDbClient.getEventDetail(
                    "12345"
            )).thenReturn(
                    new SportsDbEventResponse(
                            List.of(event)
                    )
            );

            when(contentRepository.saveAndFlush(
                    any(Content.class)
            )).thenAnswer(
                    invocation ->
                            invocation.getArgument(0)
            );

            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            assertThat(result)
                    .isSameAs(contentDto);

            verify(sportsDbClient)
                    .getEventDetail("12345");

            verify(contentRepository)
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("스포츠 경기 상세가 없으면 가져오기에 실패한다")
        void importSport_failsWhenEventDoesNotExist() {
            ContentImportRequest request =
                    new ContentImportRequest(
                            "missing-event",
                            ContentType.SPORT
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.THE_SPORTS_DB,
                            "missing-event"
                    ))
                    .thenReturn(Optional.empty());

            when(sportsDbClient.getEventDetail(
                    "missing-event"
            )).thenReturn(
                    new SportsDbEventResponse(
                            List.of()
                    )
            );

            assertThatThrownBy(() ->
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isInstanceOf(
                            IllegalArgumentException.class
                    )
                    .hasMessage(
                            "존재하지 않는 스포츠 경기입니다."
                    );

            verify(contentRepository, never())
                    .saveAndFlush(any(Content.class));
        }

        @Test
        @DisplayName("동시 저장 충돌이 발생하면 이미 저장된 콘텐츠를 반환한다")
        void importMovie_returnsExistingContentAfterConcurrentInsert() {
            ContentImportRequest request =
                    new ContentImportRequest(
                            "157336",
                            ContentType.MOVIE
                    );

            TmdbContentItem item =
                    new TmdbContentItem(
                            157336L,
                            "인터스텔라",
                            null,
                            "우주 탐사 영화",
                            null,
                            null,
                            "2014-11-05",
                            null
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "157336"
                    ))
                    .thenReturn(
                            Optional.empty(),
                            Optional.of(content)
                    );

            when(tmdbClient.getMovieDetail(
                    "157336"
            )).thenReturn(item);

            when(contentRepository.saveAndFlush(
                    any(Content.class)
            )).thenThrow(
                    new DataIntegrityViolationException(
                            "duplicate"
                    )
            );

            ContentDto result =
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    );

            assertThat(result)
                    .isSameAs(contentDto);

            verify(
                    contentRepository,
                    org.mockito.Mockito.times(2)
            ).findBySourceTypeAndExternalId(
                    ContentSourceType.TMDB_MOVIE,
                    "157336"
            );
        }

        @Test
        @DisplayName("동시 저장 충돌 후 기존 콘텐츠도 없으면 원래 예외를 다시 발생시킨다")
        void importMovie_rethrowsWhenExistingContentIsStillMissing() {
            ContentImportRequest request =
                    new ContentImportRequest(
                            "157336",
                            ContentType.MOVIE
                    );

            TmdbContentItem item =
                    new TmdbContentItem(
                            157336L,
                            "인터스텔라",
                            null,
                            "우주 탐사 영화",
                            null,
                            null,
                            "2014-11-05",
                            null
                    );

            DataIntegrityViolationException exception =
                    new DataIntegrityViolationException(
                            "duplicate"
                    );

            when(userRepository.findByEmail(
                    ADMIN_EMAIL
            )).thenReturn(
                    Optional.of(admin)
            );

            when(contentRepository
                    .findBySourceTypeAndExternalId(
                            ContentSourceType.TMDB_MOVIE,
                            "157336"
                    ))
                    .thenReturn(
                            Optional.empty(),
                            Optional.empty()
                    );

            when(tmdbClient.getMovieDetail(
                    "157336"
            )).thenReturn(item);

            when(contentRepository.saveAndFlush(
                    any(Content.class)
            )).thenThrow(exception);

            assertThatThrownBy(() ->
                    contentService.importExternalContent(
                            ADMIN_EMAIL,
                            request
                    )
            )
                    .isSameAs(exception);
        }
    }

    @Nested
    @DisplayName("콘텐츠 목록 다음 페이지")
    class GetContentsNextPageTest {

        @Test
        @DisplayName("createdAt 정렬에서 다음 페이지 커서를 생성한다")
        void getContents_createsNextCursorForCreatedAt() {
            UUID firstId =
                    UUID.randomUUID();

            UUID secondId =
                    UUID.randomUUID();

            UUID thirdId =
                    UUID.randomUUID();

            Content firstContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Content secondContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Content thirdContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Instant secondCreatedAt =
                    Instant.parse(
                            "2026-07-15T01:00:00Z"
                    );

            when(firstContent.getId())
                    .thenReturn(firstId);

            when(secondContent.getId())
                    .thenReturn(secondId);

            when(thirdContent.getId())
                    .thenReturn(thirdId);

            when(secondContent.getCreatedAt())
                    .thenReturn(secondCreatedAt);

            List<ContentQueryRow> rows =
                    List.of(
                            new ContentQueryRow(
                                    firstContent,
                                    4.0,
                                    1,
                                    10L
                            ),
                            new ContentQueryRow(
                                    secondContent,
                                    3.5,
                                    2,
                                    5L
                            ),
                            new ContentQueryRow(
                                    thirdContent,
                                    3.0,
                                    3,
                                    1L
                            )
                    );

            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    2,
                    "createdAt",
                    Direction.DESCENDING
            )).thenReturn(rows);

            when(contentMapper.toSummary(
                    any(Content.class),
                    anyDouble(),
                    anyInt()
            )).thenReturn(contentSummary);

            when(contentRepository.countContents(
                    null,
                    null
            )).thenReturn(3L);

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

            assertThat(result)
                    .isNotNull();

            verify(contentMapper)
                    .toSummary(
                            secondContent,
                            3.5,
                            2
                    );
        }

        @Test
        @DisplayName("watcherCount 정렬에서 다음 페이지 커서를 생성한다")
        void getContents_createsNextCursorForWatcherCount() {
            Content firstContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Content secondContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Content thirdContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            when(firstContent.getId())
                    .thenReturn(UUID.randomUUID());

            when(secondContent.getId())
                    .thenReturn(UUID.randomUUID());

            when(thirdContent.getId())
                    .thenReturn(UUID.randomUUID());

            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    2,
                    "watcherCount",
                    Direction.DESCENDING
            )).thenReturn(
                    List.of(
                            new ContentQueryRow(
                                    firstContent,
                                    4.0,
                                    1,
                                    10L
                            ),
                            new ContentQueryRow(
                                    secondContent,
                                    3.5,
                                    2,
                                    5L
                            ),
                            new ContentQueryRow(
                                    thirdContent,
                                    3.0,
                                    3,
                                    1L
                            )
                    )
            );

            when(contentMapper.toSummary(
                    any(Content.class),
                    anyDouble(),
                    anyInt()
            )).thenReturn(contentSummary);

            when(contentRepository.countContents(
                    null,
                    null
            )).thenReturn(3L);

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

            assertThat(result)
                    .isNotNull();
        }

        @Test
        @DisplayName("rate 정렬에서 다음 페이지 커서를 생성한다")
        void getContents_createsNextCursorForRate() {
            Content firstContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Content secondContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            Content thirdContent =
                    org.mockito.Mockito.mock(
                            Content.class
                    );

            when(firstContent.getId())
                    .thenReturn(UUID.randomUUID());

            when(secondContent.getId())
                    .thenReturn(UUID.randomUUID());

            when(thirdContent.getId())
                    .thenReturn(UUID.randomUUID());

            when(contentRepository.findContents(
                    null,
                    null,
                    null,
                    null,
                    2,
                    "rate",
                    Direction.DESCENDING
            )).thenReturn(
                    List.of(
                            new ContentQueryRow(
                                    firstContent,
                                    4.8,
                                    10,
                                    10L
                            ),
                            new ContentQueryRow(
                                    secondContent,
                                    4.5,
                                    8,
                                    5L
                            ),
                            new ContentQueryRow(
                                    thirdContent,
                                    4.0,
                                    3,
                                    1L
                            )
                    )
            );

            when(contentMapper.toSummary(
                    any(Content.class),
                    anyDouble(),
                    anyInt()
            )).thenReturn(contentSummary);

            when(contentRepository.countContents(
                    null,
                    null
            )).thenReturn(3L);

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

            assertThat(result)
                    .isNotNull();
        }
    }

}