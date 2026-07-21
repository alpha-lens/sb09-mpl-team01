package com.codeit.mpl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.Query;

@ExtendWith(MockitoExtension.class)
@DisplayName("ElasticsearchSyncService 단위 테스트")
class ElasticsearchSyncServiceTest {

    @Mock
    private ContentRepository contentRepository;

    @Mock
    private ContentSearchRepository contentSearchRepository;

    @Mock
    private ElasticsearchOperations elasticsearchOperations;

    @Mock
    private IndexOperations indexOperations;

    private ElasticsearchSyncService service;

    @BeforeEach
    void setUp() {
        service = new ElasticsearchSyncService(
                contentRepository,
                contentSearchRepository,
                elasticsearchOperations
        );
    }

    @Nested
    @DisplayName("인덱스 보장")
    class EnsureIndexWithMappingTest {

        @Test
        @DisplayName("인덱스가 없으면 생성하고 매핑을 적용한다")
        void ensureIndexWithMapping_createsIndexAndMappingWhenMissing() {
            Document mapping = mock(Document.class);

            when(elasticsearchOperations.indexOps(ContentDocument.class))
                    .thenReturn(indexOperations);
            when(indexOperations.exists()).thenReturn(false);
            when(indexOperations.createMapping(ContentDocument.class))
                    .thenReturn(mapping);

            service.ensureIndexWithMapping();

            verify(indexOperations).create();
            verify(indexOperations).createMapping(ContentDocument.class);
            verify(indexOperations).putMapping(mapping);
        }

        @Test
        @DisplayName("인덱스가 이미 있으면 생성과 매핑을 수행하지 않는다")
        void ensureIndexWithMapping_doesNothingWhenIndexExists() {
            when(elasticsearchOperations.indexOps(ContentDocument.class))
                    .thenReturn(indexOperations);
            when(indexOperations.exists()).thenReturn(true);

            service.ensureIndexWithMapping();

            verify(indexOperations, never()).create();
            verify(indexOperations, never()).createMapping(ContentDocument.class);
            verify(indexOperations, never()).putMapping(any(Document.class));
        }
    }

    @Nested
    @DisplayName("인덱스 재생성")
    class RecreateIndexWithMappingTest {

        @Test
        @DisplayName("기존 인덱스가 있으면 삭제한 뒤 새로 생성하고 매핑을 적용한다")
        void recreateIndexWithMapping_deletesExistingIndexAndRecreatesIt() {
            Document mapping = mock(Document.class);

            when(elasticsearchOperations.indexOps(ContentDocument.class))
                    .thenReturn(indexOperations);
            when(indexOperations.exists()).thenReturn(true);
            when(indexOperations.createMapping(ContentDocument.class))
                    .thenReturn(mapping);

            service.recreateIndexWithMapping();

            verify(indexOperations).delete();
            verify(indexOperations).create();
            verify(indexOperations).createMapping(ContentDocument.class);
            verify(indexOperations).putMapping(mapping);
        }

        @Test
        @DisplayName("기존 인덱스가 없으면 삭제하지 않고 생성과 매핑만 수행한다")
        void recreateIndexWithMapping_createsIndexWithoutDeletingWhenMissing() {
            Document mapping = mock(Document.class);

            when(elasticsearchOperations.indexOps(ContentDocument.class))
                    .thenReturn(indexOperations);
            when(indexOperations.exists()).thenReturn(false);
            when(indexOperations.createMapping(ContentDocument.class))
                    .thenReturn(mapping);

            service.recreateIndexWithMapping();

            verify(indexOperations, never()).delete();
            verify(indexOperations).create();
            verify(indexOperations).createMapping(ContentDocument.class);
            verify(indexOperations).putMapping(mapping);
        }
    }

    @Nested
    @DisplayName("전체 재색인")
    class ReindexAllTest {

        @Test
        @DisplayName("DB에 콘텐츠가 없으면 빈 결과를 반환한다")
        void reindexAll_returnsEmptyResultWhenDatabaseIsEmpty() {
            prepareMissingIndex();
            when(contentRepository.findAll()).thenReturn(List.of());

            ElasticsearchSyncService.SyncResult result = service.reindexAll();

            assertThat(result.total()).isZero();
            assertThat(result.synced()).isZero();
            assertThat(result.failed()).isZero();
            assertThat(result.failedIds()).isEmpty();

            verify(contentSearchRepository, never()).saveAll(any());
            verify(contentSearchRepository, never()).save(any());
        }

        @Test
        @DisplayName("모든 콘텐츠를 문서로 변환하여 배치 저장한다")
        void reindexAll_savesAllDocumentsSuccessfully() {
            Content firstContent = mock(Content.class);
            Content secondContent = mock(Content.class);
            ContentDocument firstDocument = mock(ContentDocument.class);
            ContentDocument secondDocument = mock(ContentDocument.class);

            prepareMissingIndex();
            when(contentRepository.findAll())
                    .thenReturn(List.of(firstContent, secondContent));

            try (MockedStatic<ContentDocument> mocked = mockStatic(ContentDocument.class)) {
                mocked.when(() -> ContentDocument.from(firstContent))
                        .thenReturn(firstDocument);
                mocked.when(() -> ContentDocument.from(secondContent))
                        .thenReturn(secondDocument);

                ElasticsearchSyncService.SyncResult result = service.reindexAll();

                assertThat(result.total()).isEqualTo(2);
                assertThat(result.synced()).isEqualTo(2);
                assertThat(result.failed()).isZero();
                assertThat(result.failedIds()).isEmpty();

                verify(contentSearchRepository)
                        .saveAll(List.of(firstDocument, secondDocument));
                verify(contentSearchRepository, never()).save(any());
            }
        }

        @Test
        @DisplayName("배치 저장 실패 후 단건 저장이 성공하면 동기화 성공으로 계산한다")
        void reindexAll_countsIndividualRetryAsSuccess() {
            Content content = mock(Content.class);
            ContentDocument document = mock(ContentDocument.class);

            prepareMissingIndex();
            when(contentRepository.findAll()).thenReturn(List.of(content));

            doThrow(new RuntimeException("bulk failure"))
                    .when(contentSearchRepository)
                    .saveAll(any());

            try (MockedStatic<ContentDocument> mocked = mockStatic(ContentDocument.class)) {
                mocked.when(() -> ContentDocument.from(content))
                        .thenReturn(document);

                ElasticsearchSyncService.SyncResult result = service.reindexAll();

                assertThat(result.total()).isEqualTo(1);
                assertThat(result.synced()).isEqualTo(1);
                assertThat(result.failed()).isZero();
                assertThat(result.failedIds()).isEmpty();

                verify(contentSearchRepository).saveAll(List.of(document));
                verify(contentSearchRepository).save(document);
            }
        }

        @Test
        @DisplayName("배치 저장과 단건 저장이 모두 실패하면 실패 ID를 기록한다")
        void reindexAll_recordsFailedIdWhenBatchAndIndividualSaveFail() {
            Content content = mock(Content.class);
            ContentDocument document = mock(ContentDocument.class);

            prepareMissingIndex();
            when(contentRepository.findAll()).thenReturn(List.of(content));
            when(document.getId()).thenReturn("failed-id");

            doThrow(new RuntimeException("bulk failure"))
                    .when(contentSearchRepository)
                    .saveAll(any());

            doThrow(new RuntimeException("single failure"))
                    .when(contentSearchRepository)
                    .save(document);

            try (MockedStatic<ContentDocument> mocked = mockStatic(ContentDocument.class)) {
                mocked.when(() -> ContentDocument.from(content))
                        .thenReturn(document);

                ElasticsearchSyncService.SyncResult result = service.reindexAll();

                assertThat(result.total()).isEqualTo(1);
                assertThat(result.synced()).isZero();
                assertThat(result.failed()).isEqualTo(1);
                assertThat(result.failedIds()).containsExactly("failed-id");

                verify(contentSearchRepository).saveAll(List.of(document));
                verify(contentSearchRepository).save(document);
            }
        }
    }

    @Nested
    @DisplayName("DB와 OpenSearch 불일치 검증")
    class ValidateDiffTest {

        @Test
        @DisplayName("DB 누락 문서와 OpenSearch 고아 문서를 구분한다")
        void validateDiff_findsMissingAndOrphanDocuments() {
            UUID commonId = UUID.randomUUID();
            UUID missingId = UUID.randomUUID();
            String orphanId = UUID.randomUUID().toString();

            ContentDocument commonDocument = mock(ContentDocument.class);
            ContentDocument orphanDocument = mock(ContentDocument.class);
            SearchHit<ContentDocument> commonHit = mock(SearchHit.class);
            SearchHit<ContentDocument> orphanHit = mock(SearchHit.class);
            SearchHitsIterator<ContentDocument> iterator = mock(SearchHitsIterator.class);

            prepareExistingIndex();

            when(contentRepository.count()).thenReturn(2L);
            when(contentSearchRepository.count()).thenReturn(2L);
            when(contentRepository.findAllIds())
                    .thenReturn(List.of(commonId, missingId));

            when(commonHit.getContent()).thenReturn(commonDocument);
            when(orphanHit.getContent()).thenReturn(orphanDocument);
            when(commonDocument.getId()).thenReturn(commonId.toString());
            when(orphanDocument.getId()).thenReturn(orphanId);

            org.mockito.Mockito.doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<SearchHit<ContentDocument>> consumer =
                        invocation.getArgument(0);

                consumer.accept(commonHit);
                consumer.accept(orphanHit);
                return null;
            }).when(iterator).forEachRemaining(any());

            when(elasticsearchOperations.searchForStream(
                    any(Query.class),
                    org.mockito.ArgumentMatchers.eq(ContentDocument.class)
            )).thenReturn(iterator);

            ElasticsearchSyncService.DiffResult result = service.validateDiff();

            assertThat(result.dbCount()).isEqualTo(2);
            assertThat(result.esCount()).isEqualTo(2);
            assertThat(result.missingInEs())
                    .containsExactly(missingId.toString());
            assertThat(result.orphanInEs())
                    .containsExactly(orphanId);

            verify(iterator).close();
        }

        @Test
        @DisplayName("DB와 OpenSearch ID가 같으면 불일치 목록이 비어 있다")
        void validateDiff_returnsEmptyListsWhenIdsMatch() {
            UUID id = UUID.randomUUID();

            ContentDocument document = mock(ContentDocument.class);
            SearchHit<ContentDocument> hit = mock(SearchHit.class);
            SearchHitsIterator<ContentDocument> iterator = mock(SearchHitsIterator.class);

            prepareExistingIndex();

            when(contentRepository.count()).thenReturn(1L);
            when(contentSearchRepository.count()).thenReturn(1L);
            when(contentRepository.findAllIds()).thenReturn(List.of(id));

            when(hit.getContent()).thenReturn(document);
            when(document.getId()).thenReturn(id.toString());
            org.mockito.Mockito.doAnswer(invocation -> {
                @SuppressWarnings("unchecked")
                java.util.function.Consumer<SearchHit<ContentDocument>> consumer =
                        invocation.getArgument(0);

                consumer.accept(hit);
                return null;
            }).when(iterator).forEachRemaining(any());

            when(elasticsearchOperations.searchForStream(
                    any(Query.class),
                    org.mockito.ArgumentMatchers.eq(ContentDocument.class)
            )).thenReturn(iterator);

            ElasticsearchSyncService.DiffResult result = service.validateDiff();

            assertThat(result.dbCount()).isEqualTo(1);
            assertThat(result.esCount()).isEqualTo(1);
            assertThat(result.missingInEs()).isEmpty();
            assertThat(result.orphanInEs()).isEmpty();

            verify(iterator).close();
        }
    }

    @Nested
    @DisplayName("불일치 선택 동기화")
    class SyncDiffTest {

        @Test
        @DisplayName("누락 문서를 색인하고 고아 문서를 삭제한다")
        void syncDiff_indexesMissingDocumentsAndDeletesOrphans() {
            UUID missingId = UUID.randomUUID();
            String orphanId = UUID.randomUUID().toString();

            Content missingContent = mock(Content.class);
            ContentDocument missingDocument = mock(ContentDocument.class);

            ElasticsearchSyncService spyService = spy(service);
            doReturn(new ElasticsearchSyncService.DiffResult(
                    1,
                    1,
                    List.of(missingId.toString()),
                    List.of(orphanId)
            )).when(spyService).validateDiff();

            when(contentRepository.findAllById(List.of(missingId)))
                    .thenReturn(List.of(missingContent));

            try (MockedStatic<ContentDocument> mocked = mockStatic(ContentDocument.class)) {
                mocked.when(() -> ContentDocument.from(missingContent))
                        .thenReturn(missingDocument);

                ElasticsearchSyncService.SyncResult result = spyService.syncDiff();

                assertThat(result.total()).isEqualTo(2);
                assertThat(result.synced()).isEqualTo(2);
                assertThat(result.failed()).isZero();
                assertThat(result.failedIds()).isEmpty();

                verify(contentSearchRepository)
                        .saveAll(List.of(missingDocument));
                verify(contentSearchRepository).deleteById(orphanId);
            }
        }

        @Test
        @DisplayName("동기화할 불일치가 없으면 저장과 삭제를 수행하지 않는다")
        void syncDiff_doesNothingWhenNoDifferenceExists() {
            ElasticsearchSyncService spyService = spy(service);
            doReturn(new ElasticsearchSyncService.DiffResult(
                    0,
                    0,
                    List.of(),
                    List.of()
            )).when(spyService).validateDiff();

            ElasticsearchSyncService.SyncResult result = spyService.syncDiff();

            assertThat(result.total()).isZero();
            assertThat(result.synced()).isZero();
            assertThat(result.failed()).isZero();
            assertThat(result.failedIds()).isEmpty();

            verify(contentRepository, never()).findAllById(anyList());
            verify(contentSearchRepository, never()).saveAll(any());
            verify(contentSearchRepository, never()).save(any());
            verify(contentSearchRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("누락 문서 배치 저장 실패 시 단건 저장으로 재시도한다")
        void syncDiff_retriesMissingDocumentIndividuallyAfterBatchFailure() {
            UUID missingId = UUID.randomUUID();

            Content missingContent = mock(Content.class);
            ContentDocument missingDocument = mock(ContentDocument.class);

            ElasticsearchSyncService spyService = spy(service);
            doReturn(new ElasticsearchSyncService.DiffResult(
                    1,
                    0,
                    List.of(missingId.toString()),
                    List.of()
            )).when(spyService).validateDiff();

            when(contentRepository.findAllById(List.of(missingId)))
                    .thenReturn(List.of(missingContent));

            doThrow(new RuntimeException("bulk failure"))
                    .when(contentSearchRepository)
                    .saveAll(any());

            try (MockedStatic<ContentDocument> mocked = mockStatic(ContentDocument.class)) {
                mocked.when(() -> ContentDocument.from(missingContent))
                        .thenReturn(missingDocument);

                ElasticsearchSyncService.SyncResult result = spyService.syncDiff();

                assertThat(result.total()).isEqualTo(1);
                assertThat(result.synced()).isEqualTo(1);
                assertThat(result.failed()).isZero();
                assertThat(result.failedIds()).isEmpty();

                verify(contentSearchRepository)
                        .saveAll(List.of(missingDocument));
                verify(contentSearchRepository).save(missingDocument);
            }
        }

        @Test
        @DisplayName("누락 문서 단건 저장도 실패하면 실패 ID를 기록한다")
        void syncDiff_recordsFailedIdWhenIndividualRetryFails() {
            UUID missingId = UUID.randomUUID();

            Content missingContent = mock(Content.class);
            ContentDocument missingDocument = mock(ContentDocument.class);

            ElasticsearchSyncService spyService = spy(service);
            doReturn(new ElasticsearchSyncService.DiffResult(
                    1,
                    0,
                    List.of(missingId.toString()),
                    List.of()
            )).when(spyService).validateDiff();

            when(contentRepository.findAllById(List.of(missingId)))
                    .thenReturn(List.of(missingContent));
            when(missingDocument.getId()).thenReturn("failed-document-id");

            doThrow(new RuntimeException("bulk failure"))
                    .when(contentSearchRepository)
                    .saveAll(any());

            doThrow(new RuntimeException("single failure"))
                    .when(contentSearchRepository)
                    .save(missingDocument);

            try (MockedStatic<ContentDocument> mocked = mockStatic(ContentDocument.class)) {
                mocked.when(() -> ContentDocument.from(missingContent))
                        .thenReturn(missingDocument);

                ElasticsearchSyncService.SyncResult result = spyService.syncDiff();

                assertThat(result.total()).isEqualTo(1);
                assertThat(result.synced()).isZero();
                assertThat(result.failed()).isEqualTo(1);
                assertThat(result.failedIds())
                        .containsExactly("failed-document-id");

                verify(contentSearchRepository).save(missingDocument);
            }
        }

        @Test
        @DisplayName("고아 문서 삭제 실패 시 실패 ID를 기록한다")
        void syncDiff_recordsFailedIdWhenOrphanDeletionFails() {
            String orphanId = UUID.randomUUID().toString();

            ElasticsearchSyncService spyService = spy(service);
            doReturn(new ElasticsearchSyncService.DiffResult(
                    0,
                    1,
                    List.of(),
                    List.of(orphanId)
            )).when(spyService).validateDiff();

            doThrow(new RuntimeException("delete failure"))
                    .when(contentSearchRepository)
                    .deleteById(orphanId);

            ElasticsearchSyncService.SyncResult result = spyService.syncDiff();

            assertThat(result.total()).isEqualTo(1);
            assertThat(result.synced()).isZero();
            assertThat(result.failed()).isEqualTo(1);
            assertThat(result.failedIds()).containsExactly(orphanId);

            verify(contentSearchRepository).deleteById(orphanId);
        }
    }

    private void prepareMissingIndex() {
        Document mapping = mock(Document.class);

        when(elasticsearchOperations.indexOps(ContentDocument.class))
                .thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);
        when(indexOperations.createMapping(ContentDocument.class))
                .thenReturn(mapping);
    }

    private void prepareExistingIndex() {
        when(elasticsearchOperations.indexOps(ContentDocument.class))
                .thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);
    }
}