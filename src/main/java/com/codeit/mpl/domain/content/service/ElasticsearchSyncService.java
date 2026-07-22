package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsIterator;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSyncService {

    private static final int BATCH_SIZE = 100;

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    /**
     * OpenSearch 인덱스가 없을 경우 인덱스를 새로 생성하고
     * elasticsearch-settings.json(ngram_analyzer) 및 필드 매핑을 적용합니다.
     */
    public void ensureIndexWithMapping() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ContentDocument.class);
        if (!indexOps.exists()) {
            log.info("[ES Sync] 인덱스가 존재하지 않아 새로 생성하고 매핑/설정을 적용합니다.");
            indexOps.create();
            indexOps.putMapping(indexOps.createMapping(ContentDocument.class));
        }
    }

    /**
     * 기존 OpenSearch 인덱스를 삭제 후 새로 생성하여 매핑/설정을 재적용합니다.
     */
    public void recreateIndexWithMapping() {
        IndexOperations indexOps = elasticsearchOperations.indexOps(ContentDocument.class);
        if (indexOps.exists()) {
            log.info("[ES Sync] 기존 인덱스를 삭제합니다.");
            indexOps.delete();
        }
        log.info("[ES Sync] 인덱스를 새로 생성하고 매핑/설정을 적용합니다.");
        indexOps.create();
        indexOps.putMapping(indexOps.createMapping(ContentDocument.class));
    }

    /**
     * DB의 모든 콘텐츠를 OpenSearch에 전체 재색인합니다.
     * 인덱스를 초기화하고 413 오류를 막기 위해 BATCH_SIZE(100건)씩 나눠서 인덱싱합니다.
     *
     * @return 동기화 결과 요약
     */
    @Transactional(readOnly = true)
    public SyncResult reindexAll() {
        log.info("[ES Sync] 전체 재색인 시작");
        recreateIndexWithMapping();

        List<Content> allContents = contentRepository.findAll();
        int total = allContents.size();

        if (total == 0) {
            log.info("[ES Sync] DB에 콘텐츠가 없습니다.");
            return new SyncResult(0, 0, 0, List.of());
        }

        int synced = 0;
        int failed = 0;
        List<String> failedIds = new ArrayList<>();

        for (int i = 0; i < total; i += BATCH_SIZE) {
            List<Content> batch = allContents.subList(i, Math.min(i + BATCH_SIZE, total));
            List<ContentDocument> docs = batch.stream()
                    .map(ContentDocument::from)
                    .toList();
            try {
                contentSearchRepository.saveAll(docs);
                synced += docs.size();
                log.info("[ES Sync] 진행 중: {}/{}", synced, total);
            } catch (Exception e) {
                log.error("[ES Sync] 배치 색인 실패 (offset={})", i, e);

                // 배치 실패 시 개별 재시도
                for (ContentDocument doc : docs) {
                    try {
                        contentSearchRepository.save(doc);
                        synced++;
                    } catch (Exception ex) {
                        log.error("[ES Sync] 단건 색인 실패 id={}", doc.getId(), ex);
                        failed++;
                        failedIds.add(doc.getId());
                    }
                }
            }
        }

        log.info("[ES Sync] 전체 재색인 완료 - total={}, synced={}, failed={}", total, synced, failed);
        return new SyncResult(total, synced, failed, failedIds);
    }

    /**
     * DB와 OpenSearch의 문서 수 및 ID 불일치를 검증합니다.
     *
     * @return 검증 결과 요약
     */
    @Transactional(readOnly = true)
    public DiffResult validateDiff() {
        log.info("[ES Sync] DB↔OpenSearch 불일치 검증 시작");
        ensureIndexWithMapping();

        long dbCount = contentRepository.count();
        long esCount = contentSearchRepository.count();

        List<UUID> allDbIds = contentRepository.findAllIds();
        Set<String> dbIds = allDbIds.stream()
                .map(UUID::toString)
                .collect(Collectors.toSet());

        Query query = Query.findAll();

        Set<String> esIds;
        try (SearchHitsIterator<ContentDocument> iterator =
                     elasticsearchOperations.searchForStream(query, ContentDocument.class)) {
            Iterable<SearchHit<ContentDocument>> iterable = () -> iterator;
            esIds = StreamSupport.stream(iterable.spliterator(), false)
                    .map(hit -> hit.getContent().getId())
                    .collect(Collectors.toSet());
        }

        List<String> missingInEs = dbIds.stream()
                .filter(id -> !esIds.contains(id))
                .toList();

        List<String> orphanInEs = esIds.stream()
                .filter(id -> !dbIds.contains(id))
                .toList();

        log.info(
                "[ES Sync] 검증 완료 - DB:{}, ES:{}, 누락:{}, 고아:{}",
                dbCount,
                esCount,
                missingInEs.size(),
                orphanInEs.size()
        );

        return new DiffResult(
                (int) dbCount,
                (int) esCount,
                missingInEs,
                orphanInEs
        );
    }

    /**
     * 불일치 항목만 선택적으로 동기화합니다.
     * DB에는 있지만 ES에 없는 항목만 색인하고, 고아 문서는 삭제합니다.
     *호출
     * @return 동기화 결과 요약
     */
    @Transactional(readOnly = true)
    public SyncResult syncDiff() {
        log.info("[ES Sync] 불일치 항목 선택 동기화 시작");

        DiffResult diff = validateDiff();

        int synced = 0;
        int failed = 0;
        List<String> failedIds = new ArrayList<>();

        // 1. DB에 있지만 ES에 없는 항목 색인
        if (!diff.missingInEs().isEmpty()) {
            List<UUID> missingUuids = diff.missingInEs().stream()
                    .map(UUID::fromString)
                    .toList();

            List<Content> missingContents = contentRepository.findAllById(missingUuids);
            List<ContentDocument> docs = missingContents.stream()
                    .map(ContentDocument::from)
                    .toList();

            for (int i = 0; i < docs.size(); i += BATCH_SIZE) {
                List<ContentDocument> batch =
                        docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()));

                try {
                    contentSearchRepository.saveAll(batch);
                    synced += batch.size();
                } catch (Exception e) {
                    log.error("[ES Sync] 누락 항목 색인 실패", e);

                    for (ContentDocument doc : batch) {
                        try {
                            contentSearchRepository.save(doc);
                            synced++;
                        } catch (Exception ex) {
                            failed++;
                            failedIds.add(doc.getId());
                        }
                    }
                }
            }
            log.info("[ES Sync] 누락 항목 {}건 색인 완료", synced);
        }

        // 2. ES에 있지만 DB에 없는 고아 문서 삭제
        int deletedOrphan = 0;
        for (String orphanId : diff.orphanInEs()) {
            try {
                contentSearchRepository.deleteById(orphanId);
                deletedOrphan++;
            } catch (Exception e) {
                log.error("[ES Sync] 고아 문서 삭제 실패 id={}", orphanId, e);
                failed++;
                failedIds.add(orphanId);
            }
        }

        if (deletedOrphan > 0) {
            log.info("[ES Sync] 고아 문서 {}건 삭제 완료", deletedOrphan);
        }

        return new SyncResult(
                diff.missingInEs().size() + diff.orphanInEs().size(),
                synced + deletedOrphan,
                failed,
                failedIds
        );
    }

    public record SyncResult(
            int total,
            int synced,
            int failed,
            List<String> failedIds
    ) {
    }

    public record DiffResult(
            int dbCount,
            int esCount,
            List<String> missingInEs,
            List<String> orphanInEs
    ) {
    }
}