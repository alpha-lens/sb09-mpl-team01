package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElasticsearchSyncService {

    private static final int BATCH_SIZE = 100;

    private final ContentRepository contentRepository;
    private final ContentSearchRepository contentSearchRepository;

    /**
     * DB의 모든 콘텐츠를 OpenSearch에 전체 재색인합니다.
     * 413 오류를 막기 위해 BATCH_SIZE(100건)씩 나눠서 인덱싱합니다.
     *
     * @return 동기화 결과 요약
     */
    @Transactional(readOnly = true)
    public SyncResult reindexAll() {
        log.info("[ES Sync] 전체 재색인 시작");

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
                log.error("[ES Sync] 배치 색인 실패 (offset={}): {}", i, e.getMessage());
                // 배치 실패 시 개별 재시도
                for (ContentDocument doc : docs) {
                    try {
                        contentSearchRepository.save(doc);
                        synced++;
                    } catch (Exception ex) {
                        log.error("[ES Sync] 단건 색인 실패 id={}: {}", doc.getId(), ex.getMessage());
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

        // DB의 모든 UUID
        List<Content> dbContents = contentRepository.findAll();
        Set<String> dbIds = dbContents.stream()
                .map(c -> c.getId().toString())
                .collect(Collectors.toSet());

        // OpenSearch의 모든 문서 ID (최대 10000건 조회)
        Iterable<ContentDocument> esIterable = contentSearchRepository.findAll();
        Set<String> esIds = StreamSupport.stream(esIterable.spliterator(), false)
                .map(ContentDocument::getId)
                .collect(Collectors.toSet());

        // DB에 있지만 ES에 없는 항목 (미색인)
        List<String> missingInEs = dbIds.stream()
                .filter(id -> !esIds.contains(id))
                .toList();

        // ES에 있지만 DB에 없는 항목 (고아 문서)
        List<String> orphanInEs = esIds.stream()
                .filter(id -> !dbIds.contains(id))
                .toList();

        log.info("[ES Sync] 검증 완료 - DB:{}, ES:{}, 미색인:{}, 고아:{}", 
                dbIds.size(), esIds.size(), missingInEs.size(), orphanInEs.size());

        return new DiffResult(dbIds.size(), esIds.size(), missingInEs, orphanInEs);
    }

    /**
     * 불일치 항목만 선택적으로 동기화합니다.
     * DB에는 있지만 ES에 없는 항목만 색인하고, 고아 문서는 삭제합니다.
     *
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
                List<ContentDocument> batch = docs.subList(i, Math.min(i + BATCH_SIZE, docs.size()));
                try {
                    contentSearchRepository.saveAll(batch);
                    synced += batch.size();
                } catch (Exception e) {
                    log.error("[ES Sync] 누락 항목 색인 실패: {}", e.getMessage());
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
                log.error("[ES Sync] 고아 문서 삭제 실패 id={}: {}", orphanId, e.getMessage());
                failed++;
                failedIds.add(orphanId);
            }
        }
        if (deletedOrphan > 0) {
            log.info("[ES Sync] 고아 문서 {}건 삭제 완료", deletedOrphan);
        }

        return new SyncResult(diff.missingInEs().size() + diff.orphanInEs().size(), synced + deletedOrphan, failed, failedIds);
    }

    public record SyncResult(int total, int synced, int failed, List<String> failedIds) {}

    public record DiffResult(int dbCount, int esCount, List<String> missingInEs, List<String> orphanInEs) {}
}
