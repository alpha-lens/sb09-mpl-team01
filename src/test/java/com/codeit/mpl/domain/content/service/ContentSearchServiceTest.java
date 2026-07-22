package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentSearchService 단위 테스트")
class ContentSearchServiceTest {

    @Mock
    private ContentSearchRepository contentSearchRepository;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    private ContentSearchService contentSearchService;

    @BeforeEach
    void setUp() {
        contentSearchService = new ContentSearchService(
                contentSearchRepository,
                elasticsearchClient
        );
    }

    @Test
    @DisplayName("초성 검색어가 들어오면 searchByChosung을 호출한다")
    void search_chosungKeyword() {
        // Given
        String keyword = "ㅇㅌㅅㅌㄹ";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        
        when(contentSearchRepository.searchByChosung(eq("ㅇㅌㅅㅌㄹ"), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // Then
        assertThat(result).isSameAs(expectedPage);
        verify(contentSearchRepository).searchByChosung(eq("ㅇㅌㅅㅌㄹ"), eq(pageable));
        verify(contentSearchRepository, never()).searchByKeyword(any(), any());
    }

    @Test
    @DisplayName("10자 초과 초성 검색어가 들어오면 10자로 잘라서 searchByChosung을 호출한다")
    void search_chosungKeywordExceedingTenChars() {
        // Given
        String keyword = "ㅇㄴㅌㅅㅌㄹㅇㅇㅌㅅ"; // 11자
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());

        when(contentSearchRepository.searchByChosung(eq("ㅇㄴㅌㅅㅌㄹㅇㅇㅌㅅ".substring(0, 10)), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // Then
        assertThat(result).isSameAs(expectedPage);
        verify(contentSearchRepository).searchByChosung(eq("ㅇㄴㅌㅅㅌㄹㅇㅇㅌㅅ".substring(0, 10)), eq(pageable));
        verify(contentSearchRepository, never()).searchByKeyword(any(), any());
    }

    @Test
    @DisplayName("일반 단일 검색어가 들어오면 searchByKeyword를 호출한다")
    void search_singleKeyword() {
        // Given
        String keyword = "영화";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        
        when(contentSearchRepository.searchByKeyword(eq("영화"), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // Then
        assertThat(result).isSameAs(expectedPage);
        verify(contentSearchRepository).searchByKeyword(eq("영화"), eq(pageable));
        verify(contentSearchRepository, never()).searchByChosung(any(), any());
    }

    @Test
    @DisplayName("띄어쓰기가 포함된 검색어가 들어오고 nori 분석 토큰이 1개 이하이면 searchByKeyword를 호출한다")
    @SuppressWarnings("unchecked")
    void search_multiKeywordWithOneToken() throws Exception {
        // Given
        String keyword = "인기 영화";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());

        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        when(elasticsearchClient.indices()).thenReturn(indicesClient);

        AnalyzeResponse response = mock(AnalyzeResponse.class);
        AnalyzeToken token = mock(AnalyzeToken.class);
        when(token.token()).thenReturn("영화");
        when(response.tokens()).thenReturn(List.of(token));

        when(indicesClient.analyze(any(Function.class))).thenReturn(response);
        when(contentSearchRepository.searchByKeyword(eq("인기 영화"), eq(pageable)))
                .thenReturn(expectedPage);

        // When
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // Then
        assertThat(result).isSameAs(expectedPage);
        verify(contentSearchRepository).searchByKeyword(eq("인기 영화"), eq(pageable));
    }
}
