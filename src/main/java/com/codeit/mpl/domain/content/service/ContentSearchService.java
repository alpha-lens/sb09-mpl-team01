package com.codeit.mpl.domain.content.service;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.AnalyzeResponse;
import co.elastic.clients.elasticsearch.indices.analyze.AnalyzeToken;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSearchService {

    private final ContentSearchRepository contentSearchRepository;
    private final co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    /**
     * 키워드를 기반으로 Elasticsearch에서 콘텐츠를 검색합니다.
     */
    public Page<ContentDocument> search(String keywordLike, Pageable pageable) {
        boolean isChosung = keywordLike.trim().matches("^[\\u3131-\\u314e\\s]+$");

        if (isChosung) {
            String chosungKeyword = keywordLike.trim().replaceAll("\\s+", "");
            return contentSearchRepository.searchByChosung(chosungKeyword, pageable);
        }

        String trimmedKeyword = keywordLike.trim();
        if (trimmedKeyword.contains(" ")) {
            List<String> tokens = analyzeKeywordWithNori(trimmedKeyword);

            if (tokens.size() > 1) {
                EsSearchResult multiTokenResult = searchByMultiToken(tokens, pageable);
                return new PageImpl<>(multiTokenResult.documents(), pageable, multiTokenResult.totalCount());
            }
        }

        return contentSearchRepository.searchByKeyword(trimmedKeyword, pageable);
    }

    private List<String> analyzeKeywordWithNori(String keyword) {
        if (elasticsearchClient == null) {
            return List.of(keyword);
        }
        try {
            AnalyzeResponse response = elasticsearchClient
                    .indices()
                    .analyze(a -> a
                            .index("contents")
                            .analyzer("nori_analyzer")
                            .text(keyword)
                    );

            List<String> tokens = response.tokens()
                    .stream()
                    .map(AnalyzeToken::token)
                    .filter(Objects::nonNull)
                    .filter(t -> !t.isBlank())
                    .toList();

            return tokens.isEmpty()
                    ? List.of(keyword)
                    : tokens;

        } catch (Exception exception) {
            log.warn(
                    "Nori tokenization failed for keyword: {}, falling back to raw keyword. Reason: {}",
                    keyword,
                    exception.getMessage()
            );

            return List.of(keyword);
        }
    }

    private EsSearchResult searchByMultiToken(List<String> tokens, Pageable pageable) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            for (String token : tokens) {
                Query titleMatch = Query.of(q -> q
                        .match(m -> m
                                .field("title")
                                .query(token)
                                .fuzziness("AUTO")
                                .boost(3.0f)
                        )
                );

                Query titleAutoMatch = Query.of(q -> q
                        .match(m -> m
                                .field("title.autocomplete")
                                .query(token)
                                .boost(2.5f)
                        )
                );

                Query tagsMatch = Query.of(q -> q
                        .match(m -> m
                                .field("tags")
                                .query(token)
                                .fuzziness("AUTO")
                                .boost(2.0f)
                        )
                );

                Query tagsAutoMatch = Query.of(q -> q
                        .match(m -> m
                                .field("tags.autocomplete")
                                .query(token)
                                .boost(1.5f)
                        )
                );

                Query descMatch = Query.of(q -> q
                        .match(m -> m
                                .field("description")
                                .query(token)
                                .fuzziness("AUTO")
                        )
                );

                Query tokenQuery = Query.of(q -> q
                        .bool(b -> b
                                .should(
                                        titleMatch,
                                        titleAutoMatch,
                                        tagsMatch,
                                        tagsAutoMatch,
                                        descMatch
                                )
                        )
                );

                boolBuilder.must(tokenQuery);
            }

            var searchResponse = elasticsearchClient.search(s -> s
                    .index("contents")
                    .query(q -> q
                            .bool(boolBuilder.build())
                    )
                    .size(pageable.getPageSize()),
                    ContentDocument.class
            );

            List<ContentDocument> docs = searchResponse.hits()
                    .hits()
                    .stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();

            long totalCount = searchResponse.hits().total().value();
            return new EsSearchResult(docs, totalCount);

        } catch (Exception exception) {
            log.error(
                    "Multi-token search failed, fallback to repository searchByKeyword",
                    exception
            );

            String joined = String.join(" ", tokens);
            Page<ContentDocument> fallbackPage = contentSearchRepository.searchByKeyword(
                    joined,
                    pageable
            );

            return new EsSearchResult(
                    fallbackPage.getContent(),
                    fallbackPage.getTotalElements()
            );
        }
    }

    private record EsSearchResult(List<ContentDocument> documents, long totalCount) {}
}
