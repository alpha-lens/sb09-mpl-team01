package com.codeit.mpl.domain.content.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.core.search.TotalHitsRelation;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContentSearchServiceTest {

    @Mock
    private ContentSearchRepository contentSearchRepository;

    @Mock
    private ElasticsearchClient elasticsearchClient;

    @InjectMocks
    private ContentSearchService contentSearchService;

    @Test
    @DisplayName("한글 초성 검색어 입력 시 searchByChosung이 호출된다")
    void search_withChosung_callsSearchByChosung() {
        // given
        String chosung = "ㄱㄴㄷ";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        given(contentSearchRepository.searchByChosung(eq("ㄱㄴㄷ"), any(Pageable.class))).willReturn(expectedPage);

        // when
        Page<ContentDocument> result = contentSearchService.search(chosung, pageable);

        // then
        assertThat(result).isNotNull();
        verify(contentSearchRepository).searchByChosung(eq("ㄱㄴㄷ"), any(Pageable.class));
    }

    @Test
    @DisplayName("10자 초과 초성 검색어 입력 시 10자로 잘라서 searchByChosung이 호출된다")
    void search_withLongChosung_truncatesTo10Chars() {
        // given
        String longChosung = "ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊㅋㅌㅍㅎ";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        given(contentSearchRepository.searchByChosung(eq("ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊ"), any(Pageable.class))).willReturn(expectedPage);

        // when
        Page<ContentDocument> result = contentSearchService.search(longChosung, pageable);

        // then
        assertThat(result).isNotNull();
        verify(contentSearchRepository).searchByChosung(eq("ㄱㄴㄷㄹㅁㅂㅅㅇㅈㅊ"), any(Pageable.class));
    }

    @Test
    @DisplayName("공백이 없는 일반 키워드 입력 시 searchByKeyword가 호출된다")
    void search_withSingleKeyword_callsSearchByKeyword() {
        // given
        String keyword = "기생충";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        given(contentSearchRepository.searchByKeyword(eq("기생충"), any(Pageable.class))).willReturn(expectedPage);

        // when
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // then
        assertThat(result).isNotNull();
        verify(contentSearchRepository).searchByKeyword(eq("기생충"), any(Pageable.class));
    }

    @Test
    @DisplayName("elasticsearchClient가 null이고 공백 포함 키워드 검색 시 searchByKeyword fallback 동작한다")
    void search_withNullElasticsearchClient_fallsBackToRawKeyword() {
        // given
        ContentSearchService nullEsService = new ContentSearchService(contentSearchRepository, null);
        String keyword = "기생충 영화";
        Pageable pageable = PageRequest.of(0, 10);
        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        given(contentSearchRepository.searchByKeyword(eq("기생충 영화"), any(Pageable.class))).willReturn(expectedPage);

        // when
        Page<ContentDocument> result = nullEsService.search(keyword, pageable);

        // then
        assertThat(result).isNotNull();
        verify(contentSearchRepository).searchByKeyword(eq("기생충 영화"), any(Pageable.class));
    }

    @Test
    @DisplayName("Nori 토큰화 중 예외 발생 시 원본 키워드로 searchByKeyword fallback 동작한다")
    void search_whenNoriFails_fallsBackToRawKeyword() throws Exception {
        // given
        String keyword = "기생충 영화";
        Pageable pageable = PageRequest.of(0, 10);
        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);
        given(indicesClient.analyze(any(Function.class))).willThrow(new RuntimeException("Nori error"));

        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        given(contentSearchRepository.searchByKeyword(eq("기생충 영화"), any(Pageable.class))).willReturn(expectedPage);

        // when
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // then
        assertThat(result).isNotNull();
        verify(contentSearchRepository).searchByKeyword(eq("기생충 영화"), any(Pageable.class));
    }

    @Test
    @DisplayName("MultiToken 검색 중 ES 검색 예외 발생 시 repository searchByKeyword fallback 동작한다")
    void search_whenMultiTokenSearchFails_fallsBackToRepositorySearch() throws Exception {
        // given
        String keyword = "기생충 영화";
        Pageable pageable = PageRequest.of(0, 10);

        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);

        AnalyzeResponse analyzeResponse = mock(AnalyzeResponse.class);
        AnalyzeToken token1 = mock(AnalyzeToken.class);
        given(token1.token()).willReturn("기생충");
        AnalyzeToken token2 = mock(AnalyzeToken.class);
        given(token2.token()).willReturn("영화");
        given(analyzeResponse.tokens()).willReturn(List.of(token1, token2));

        given(indicesClient.analyze(any(Function.class))).willReturn(analyzeResponse);
        given(elasticsearchClient.search(any(Function.class), eq(ContentDocument.class)))
                .willThrow(new RuntimeException("Search failed"));

        Page<ContentDocument> expectedPage = new PageImpl<>(List.of());
        given(contentSearchRepository.searchByKeyword(eq("기생충 영화"), any(Pageable.class))).willReturn(expectedPage);

        // when
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // then
        assertThat(result).isNotNull();
        verify(contentSearchRepository).searchByKeyword(eq("기생충 영화"), any(Pageable.class));
    }

    @Test
    @DisplayName("MultiToken 검색 성공 시 ES 검색 결과를 Page 객체로 반환한다")
    void search_multiTokenSuccess_returnsEsSearchResult() throws Exception {
        // given
        String keyword = "기생충 영화";
        Pageable pageable = PageRequest.of(0, 10);

        ElasticsearchIndicesClient indicesClient = mock(ElasticsearchIndicesClient.class);
        given(elasticsearchClient.indices()).willReturn(indicesClient);

        AnalyzeResponse analyzeResponse = mock(AnalyzeResponse.class);
        AnalyzeToken token1 = mock(AnalyzeToken.class);
        given(token1.token()).willReturn("기생충");
        AnalyzeToken token2 = mock(AnalyzeToken.class);
        given(token2.token()).willReturn("영화");
        given(analyzeResponse.tokens()).willReturn(List.of(token1, token2));

        given(indicesClient.analyze(any(java.util.function.Function.class))).willReturn(analyzeResponse);

        ContentDocument doc = mock(ContentDocument.class);
        Hit<ContentDocument> hit = mock(Hit.class);
        given(hit.source()).willReturn(doc);

        HitsMetadata<ContentDocument> hitsMetadata = mock(HitsMetadata.class);
        given(hitsMetadata.hits()).willReturn(List.of(hit));

        TotalHits totalHits = new TotalHits.Builder().value(1L).relation(TotalHitsRelation.Eq).build();
        given(hitsMetadata.total()).willReturn(totalHits);

        SearchResponse<ContentDocument> searchResponse = mock(SearchResponse.class);
        given(searchResponse.hits()).willReturn(hitsMetadata);

        given(elasticsearchClient.search(any(java.util.function.Function.class), eq(ContentDocument.class)))
                .willReturn(searchResponse);

        // when
        Page<ContentDocument> result = contentSearchService.search(keyword, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1L);
    }
}
