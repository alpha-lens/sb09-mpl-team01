package com.codeit.mpl.domain.content.controller;

import com.codeit.mpl.domain.content.dto.request.ContentCreateRequest;
import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.request.ContentUpdateRequest;
import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.dto.response.ContentSummary;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.service.ContentService;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    @Mock
    private ContentService contentService;

    @Mock
    private UserDetails userDetails;

    private ContentController contentController;

    @BeforeEach
    void setUp() {
        contentController =
                new ContentController(
                        contentService
                );
    }

    @Test
    @DisplayName("관리자가 콘텐츠를 생성하면 생성된 콘텐츠를 반환한다")
    void createContent_success() {
        // given
        String requesterEmail =
                "admin@mopl.io";

        ContentCreateRequest request =
                new ContentCreateRequest(
                        ContentType.MOVIE,
                        "인터스텔라",
                        "우주를 배경으로 한 영화",
                        List.of(
                                "MOVIE",
                                "SF"
                        )
                );

        ContentDto expectedResponse =
                createContentDto(
                        ContentType.MOVIE,
                        "인터스텔라"
                );

        when(userDetails.getUsername())
                .thenReturn(requesterEmail);

        when(
                contentService.createContent(
                        requesterEmail,
                        request,
                        null
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<ContentDto> response =
                contentController.createContent(
                        userDetails,
                        request,
                        null
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService).createContent(
                requesterEmail,
                request,
                null
        );
    }

    @Test
    @DisplayName("관리자가 외부 콘텐츠를 Import하면 저장된 콘텐츠를 반환한다")
    void importExternalContent_success() {
        // given
        String requesterEmail =
                "admin@mopl.io";

        ContentImportRequest request =
                new ContentImportRequest(
                        "157336",
                        ContentType.MOVIE
                );

        ContentDto expectedResponse =
                createContentDto(
                        ContentType.MOVIE,
                        "인터스텔라"
                );

        when(userDetails.getUsername())
                .thenReturn(requesterEmail);

        when(
                contentService.importExternalContent(
                        requesterEmail,
                        request
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<ContentDto> response =
                contentController
                        .importExternalContent(
                                userDetails,
                                request
                        );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService)
                .importExternalContent(
                        requesterEmail,
                        request
                );
    }

    @Test
    @DisplayName("콘텐츠 ID로 단건 콘텐츠를 조회한다")
    void getContent_success() {
        // given
        UUID contentId =
                UUID.randomUUID();

        ContentDto expectedResponse =
                createContentDto(
                        ContentType.TVSERIES,
                        "드라마"
                );

        when(
                contentService.getContent(
                        contentId
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<ContentDto> response =
                contentController.getContent(
                        contentId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService).getContent(
                contentId
        );
    }

    @Test
    @DisplayName("콘텐츠를 수정하면 수정된 콘텐츠를 반환한다")
    void updateContent_success() {
        // given
        String requesterEmail =
                "admin@mopl.io";

        UUID contentId =
                UUID.randomUUID();

        ContentUpdateRequest request =
                new ContentUpdateRequest(
                        "수정된 제목",
                        "수정된 설명",
                        List.of(
                                "MOVIE",
                                "UPDATED"
                        )
                );

        ContentDto expectedResponse =
                createContentDto(
                        ContentType.MOVIE,
                        "수정된 제목"
                );

        when(userDetails.getUsername())
                .thenReturn(requesterEmail);

        when(
                contentService.updateContent(
                        requesterEmail,
                        contentId,
                        request,
                        null
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<ContentDto> response =
                contentController.updateContent(
                        userDetails,
                        contentId,
                        request,
                        null
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService).updateContent(
                requesterEmail,
                contentId,
                request,
                null
        );
    }

    @Test
    @DisplayName("콘텐츠를 삭제하면 204 응답을 반환한다")
    void deleteContent_success() {
        // given
        String requesterEmail =
                "admin@mopl.io";

        UUID contentId =
                UUID.randomUUID();

        when(userDetails.getUsername())
                .thenReturn(requesterEmail);

        // when
        ResponseEntity<Void> response =
                contentController.deleteContent(
                        userDetails,
                        contentId
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(response.getBody())
                .isNull();

        verify(contentService).deleteContent(
                requesterEmail,
                contentId
        );
    }

    @Test
    @DisplayName("typeEqual이 없으면 전체 콘텐츠 목록을 조회한다")
    void getContents_withoutType_success() {
        // given
        String keywordLike =
                "콘텐츠";

        int limit = 20;

        String sortBy =
                "createdAt";

        Direction sortDirection =
                Direction.DESCENDING;

        CursorPageResponseDto<ContentSummary>
                expectedResponse =
                mockCursorPageResponse();

        when(
                contentService.getContents(
                        null,
                        null,
                        keywordLike,
                        null,
                        limit,
                        sortBy,
                        sortDirection
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<
                CursorPageResponseDto<ContentSummary>
                > response =
                contentController.getContents(
                        null,
                        null,
                        keywordLike,
                        null,
                        limit,
                        sortBy,
                        sortDirection
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService).getContents(
                null,
                null,
                keywordLike,
                null,
                limit,
                sortBy,
                sortDirection
        );
    }

    @Test
    @DisplayName("typeEqual이 빈 문자열이면 전체 콘텐츠 목록을 조회한다")
    void getContents_withBlankType_success() {
        // given
        CursorPageResponseDto<ContentSummary>
                expectedResponse =
                mockCursorPageResponse();

        when(
                contentService.getContents(
                        null,
                        null,
                        null,
                        null,
                        20,
                        "createdAt",
                        Direction.DESCENDING
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<
                CursorPageResponseDto<ContentSummary>
                > response =
                contentController.getContents(
                        null,
                        null,
                        null,
                        "   ",
                        20,
                        "createdAt",
                        Direction.DESCENDING
                );

        // then
        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService).getContents(
                null,
                null,
                null,
                null,
                20,
                "createdAt",
                Direction.DESCENDING
        );
    }

    @Test
    @DisplayName("typeEqual movie를 MOVIE 타입으로 변환한다")
    void getContents_withMovieType_success() {
        verifyConvertedContentType(
                "movie",
                ContentType.MOVIE
        );
    }

    @Test
    @DisplayName("typeEqual tvSeries를 TVSERIES 타입으로 변환한다")
    void getContents_withTvSeriesType_success() {
        verifyConvertedContentType(
                "tvSeries",
                ContentType.TVSERIES
        );
    }

    @Test
    @DisplayName("typeEqual sport를 SPORT 타입으로 변환한다")
    void getContents_withSportType_success() {
        verifyConvertedContentType(
                "sport",
                ContentType.SPORT
        );
    }

    @Test
    @DisplayName("대문자 enum 형식의 typeEqual도 변환한다")
    void getContents_withUppercaseTypes_success() {
        verifyConvertedContentType(
                "MOVIE",
                ContentType.MOVIE
        );

        verifyConvertedContentType(
                "TVSERIES",
                ContentType.TVSERIES
        );

        verifyConvertedContentType(
                "SPORT",
                ContentType.SPORT
        );
    }

    @Test
    @DisplayName("typeEqual 앞뒤 공백을 제거한 후 타입을 변환한다")
    void getContents_withTrimmedType_success() {
        verifyConvertedContentType(
                "  movie  ",
                ContentType.MOVIE
        );
    }

    @Test
    @DisplayName("지원하지 않는 typeEqual이면 예외가 발생한다")
    void getContents_withInvalidType_throwsException() {
        // when & then
        assertThatThrownBy(() ->
                contentController.getContents(
                        null,
                        null,
                        null,
                        "documentary",
                        20,
                        "createdAt",
                        Direction.DESCENDING
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "typeEqual은 movie, tvSeries, sport만 사용할 수 있습니다."
                );

        verify(
                contentService,
                never()
        ).getContents(
                null,
                null,
                null,
                null,
                20,
                "createdAt",
                Direction.DESCENDING
        );
    }

    @Test
    @DisplayName("외부 API에서 영화 콘텐츠를 검색한다")
    void searchExternalContents_success() {
        // given
        String keyword =
                "interstellar";

        ContentType type =
                ContentType.MOVIE;

        List<ExternalContentSearchResult>
                expectedResponse =
                List.of(
                        new ExternalContentSearchResult(
                                "157336",
                                ContentType.MOVIE,
                                "인터스텔라",
                                "우주 탐사 영화",
                                "https://image.tmdb.org/poster.jpg",
                                "2014-11-05"
                        )
                );

        when(
                contentService.searchExternalContents(
                        keyword,
                        type
                )
        ).thenReturn(expectedResponse);

        // when
        ResponseEntity<
                List<ExternalContentSearchResult>
                > response =
                contentController.searchExternalContents(
                        keyword,
                        type
                );

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService)
                .searchExternalContents(
                        keyword,
                        type
                );
    }

    /**
     * typeEqual 문자열이 올바른 ContentType으로 변환되어
     * ContentService에 전달되는지 확인합니다.
     */
    private void verifyConvertedContentType(
            String typeEqual,
            ContentType expectedType
    ) {
        CursorPageResponseDto<ContentSummary>
                expectedResponse =
                mockCursorPageResponse();

        when(
                contentService.getContents(
                        null,
                        null,
                        null,
                        expectedType,
                        20,
                        "createdAt",
                        Direction.DESCENDING
                )
        ).thenReturn(expectedResponse);

        ResponseEntity<
                CursorPageResponseDto<ContentSummary>
                > response =
                contentController.getContents(
                        null,
                        null,
                        null,
                        typeEqual,
                        20,
                        "createdAt",
                        Direction.DESCENDING
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(response.getBody())
                .isSameAs(expectedResponse);

        verify(contentService).getContents(
                null,
                null,
                null,
                expectedType,
                20,
                "createdAt",
                Direction.DESCENDING
        );
    }

    @SuppressWarnings("unchecked")
    private CursorPageResponseDto<ContentSummary>
    mockCursorPageResponse() {
        return mock(
                CursorPageResponseDto.class
        );
    }

    private ContentDto createContentDto(
            ContentType type,
            String title
    ) {
        return new ContentDto(
                UUID.randomUUID(),
                type,
                title,
                "콘텐츠 설명",
                "https://example.com/thumbnail.jpg",
                List.of(type.name()),
                4.5,
                10,
                3L
        );
    }
}