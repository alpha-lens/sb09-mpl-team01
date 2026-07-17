package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.mockito.Mockito;

@ActiveProfiles("test")
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=",
    "spring.data.elasticsearch.repositories.enabled=true",
    "spring.data.redis.repositories.enabled=false",
    "spring.elasticsearch.uris=http://localhost:9200"
})
class ContentSearchIntegrationTest {

    @Autowired
    private ContentSearchRepository contentSearchRepository;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public RedisConnectionFactory redisConnectionFactory() {
            return Mockito.mock(RedisConnectionFactory.class);
        }

        @Bean
        @Primary
        public RedisTemplate<String, Object> redisTemplate() {
            return Mockito.mock(RedisTemplate.class);
        }

        @Bean
        @Primary
        public StringRedisTemplate stringRedisTemplate() {
            return Mockito.mock(StringRedisTemplate.class);
        }

        @Bean
        @Primary
        public RedisMessageListenerContainer redisMessageListenerContainer() {
            return Mockito.mock(RedisMessageListenerContainer.class);
        }

        @Bean
        @Primary
        public KafkaTemplate<String, Object> kafkaTemplate() {
            return Mockito.mock(org.springframework.kafka.core.KafkaTemplate.class);
        }
    }

    @BeforeEach
    void setUp() {
        contentSearchRepository.deleteAll();
    }

    @Test
    void testElasticsearchConnectionAndSearch() {
        // Given
        UUID contentId1 = UUID.randomUUID();
        Content content1 = Mockito.mock(Content.class);
        Mockito.when(content1.getId()).thenReturn(contentId1);
        Mockito.when(content1.getTitle()).thenReturn("기생충");
        Mockito.when(content1.getDescription()).thenReturn("봉준호 감독의 영화");
        Mockito.when(content1.getType()).thenReturn(ContentType.MOVIE);
        Mockito.when(content1.getSourceType()).thenReturn("TMDB");
        Mockito.when(content1.getExternalId()).thenReturn("12345");
        Mockito.when(content1.getTags()).thenReturn(List.of("스릴러", "드라마"));
        Mockito.when(content1.getThumbnailUrl()).thenReturn("http://image.com/1");
        Mockito.when(content1.getContentUrl()).thenReturn("http://content.com/1");
        Mockito.when(content1.getCreatedAt()).thenReturn(Instant.now());

        ContentDocument doc1 = ContentDocument.from(content1);

        UUID contentId2 = UUID.randomUUID();
        Content content2 = Mockito.mock(Content.class);
        Mockito.when(content2.getId()).thenReturn(contentId2);
        Mockito.when(content2.getTitle()).thenReturn("아바타");
        Mockito.when(content2.getDescription()).thenReturn("제임스 카메론의 영화");
        Mockito.when(content2.getType()).thenReturn(ContentType.MOVIE);
        Mockito.when(content2.getSourceType()).thenReturn("TMDB");
        Mockito.when(content2.getExternalId()).thenReturn("67890");
        Mockito.when(content2.getTags()).thenReturn(List.of("SF", "액션"));
        Mockito.when(content2.getThumbnailUrl()).thenReturn("http://image.com/2");
        Mockito.when(content2.getContentUrl()).thenReturn("http://content.com/2");
        Mockito.when(content2.getCreatedAt()).thenReturn(Instant.now());

        ContentDocument doc2 = ContentDocument.from(content2);

        // When: Save to Elasticsearch 7.10
        contentSearchRepository.save(doc1);
        contentSearchRepository.save(doc2);

        // Then 1: Search by Keyword
        Page<ContentDocument> keywordResults = contentSearchRepository.searchByKeyword("기생충", PageRequest.of(0, 10));
        assertThat(keywordResults.getContent()).isNotEmpty();
        assertThat(keywordResults.getContent().get(0).getTitle()).isEqualTo("기생충");

        // Then 2: Search by Chosung (기생충 -> ㄱㅅㅊ)
        Page<ContentDocument> chosungResults = contentSearchRepository.searchByChosung("ㄱㅅㅊ", PageRequest.of(0, 10));
        assertThat(chosungResults.getContent()).isNotEmpty();
        assertThat(chosungResults.getContent().get(0).getTitle()).isEqualTo("기생충");
    }

    @Autowired
    private co.elastic.clients.elasticsearch.ElasticsearchClient elasticsearchClient;

    @Test
    void testBulkInsertPerformanceWithTuning() throws Exception {
        int totalCount = 10000;
        int batchSize = 2000;

        // 1. refresh_interval 비활성화 (-1)
        elasticsearchClient.indices().putSettings(s -> s
            .index("contents")
            .settings(se -> se.refreshInterval(r -> r.time("-1")))
        );

        long startTime = System.currentTimeMillis();

        // 2. 벌크 데이터 생성 및 분할 전송
        java.util.List<co.elastic.clients.elasticsearch.core.bulk.BulkOperation> operations = new java.util.ArrayList<>();
        for (int i = 0; i < totalCount; i++) {
            final int index = i;
            String docId = UUID.randomUUID().toString();
            
            ContentDocument doc = ContentDocument.builder()
                .id(docId)
                .title("영화 제목 " + index)
                .titleChosung(com.codeit.mpl.domain.content.util.HangulUtils.extractChosung("영화 제목 " + index))
                .description("봉준호 감독 스타일의 가상 영화 줄거리 " + index)
                .type("MOVIE")
                .sourceType("TEST")
                .externalId("EXT-" + index)
                .tags(List.of("드라마", "스릴러"))
                .tagsChosung(List.of(com.codeit.mpl.domain.content.util.HangulUtils.extractChosung("드라마"), com.codeit.mpl.domain.content.util.HangulUtils.extractChosung("스릴러")))
                .createdAt(Instant.now())
                .build();

            operations.add(co.elastic.clients.elasticsearch.core.bulk.BulkOperation.of(op -> op
                .index(idx -> idx
                    .index("contents")
                    .id(doc.getId())
                    .document(doc)
                )
            ));

            if (operations.size() >= batchSize || i == totalCount - 1) {
                co.elastic.clients.elasticsearch.core.BulkRequest bulkRequest = co.elastic.clients.elasticsearch.core.BulkRequest.of(b -> b.operations(operations));
                elasticsearchClient.bulk(bulkRequest);
                operations.clear();
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        System.out.println("====== 벌크 인서트 검증 결과 ======");
        System.out.println("삽입된 문서 개수: " + totalCount + " 개");
        System.out.println("배치 크기 (Batch Size): " + batchSize + " 개");
        System.out.println("소요 시간: " + duration + " ms (" + (duration / 1000.0) + " 초)");
        System.out.println("==================================");

        // 3. refresh_interval 원복 (1s) 및 리프레시 강제 실행
        elasticsearchClient.indices().putSettings(s -> s
            .index("contents")
            .settings(se -> se.refreshInterval(r -> r.time("1s")))
        );
        elasticsearchClient.indices().refresh(r -> r.index("contents"));

        // 검증: 전체 카운트가 맞는지 확인
        long count = contentSearchRepository.count();
        assertThat(count).isGreaterThanOrEqualTo((long) totalCount);
    }
}
