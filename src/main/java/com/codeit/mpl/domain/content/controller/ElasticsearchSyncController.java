package com.codeit.mpl.domain.content.controller;

import com.codeit.mpl.domain.content.service.ElasticsearchSyncService;
import com.codeit.mpl.domain.content.service.ElasticsearchSyncService.DiffResult;
import com.codeit.mpl.domain.content.service.ElasticsearchSyncService.SyncResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenSearch 수동 동기화 관리자 전용 API.
 *
 * <p>DB↔OpenSearch 불일치가 발생했을 때 관리자가 직접 호출하여 동기화합니다.
 *
 * <pre>
 * GET  /api/admin/es-sync/diff      : DB와 ES의 불일치 항목 검증만 수행 (읽기 전용)
 * POST /api/admin/es-sync/diff      : 불일치 항목만 선택적으로 동기화 (누락 색인 + 고아 삭제)
 * POST /api/admin/es-sync/reindex   : DB 전체를 ES에 재색인 (전체 덮어쓰기)
 * </pre>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/es-sync")
public class ElasticsearchSyncController {

    private final ElasticsearchSyncService elasticsearchSyncService;

    /**
     * DB와 OpenSearch의 문서 수 및 불일치 ID 목록을 반환합니다. (읽기 전용)
     */
    @GetMapping("/diff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DiffResult> validateDiff() {
        log.info("[ES Sync API] DB↔ES 불일치 검증 요청");
        DiffResult result = elasticsearchSyncService.validateDiff();
        return ResponseEntity.ok(result);
    }

    /**
     * DB에만 있는 항목은 ES에 색인하고, ES에만 있는 고아 문서는 삭제합니다.
     * 전체 재색인보다 빠르며, 정상 운영 중 불일치 보정에 사용합니다.
     */
    @PostMapping("/diff")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncResult> syncDiff() {
        log.info("[ES Sync API] 불일치 항목 선택 동기화 요청");
        SyncResult result = elasticsearchSyncService.syncDiff();
        return ResponseEntity.ok(result);
    }

    /**
     * DB의 모든 콘텐츠를 OpenSearch에 전체 재색인합니다.
     * 413 오류를 막기 위해 100건씩 배치로 나눠서 인덱싱합니다.
     * 데이터가 많을 경우 처리 시간이 길어질 수 있습니다.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SyncResult> reindexAll() {
        log.info("[ES Sync API] 전체 재색인 요청");
        SyncResult result = elasticsearchSyncService.reindexAll();
        return ResponseEntity.ok(result);
    }
}
