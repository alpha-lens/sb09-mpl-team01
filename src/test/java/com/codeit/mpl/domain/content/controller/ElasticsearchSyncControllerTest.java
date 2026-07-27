package com.codeit.mpl.domain.content.controller;

import com.codeit.mpl.domain.content.service.ElasticsearchSyncService;
import com.codeit.mpl.domain.content.service.ElasticsearchSyncService.DiffResult;
import com.codeit.mpl.domain.content.service.ElasticsearchSyncService.SyncResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ElasticsearchSyncControllerTest {

    @Mock
    private ElasticsearchSyncService elasticsearchSyncService;

    @InjectMocks
    private ElasticsearchSyncController elasticsearchSyncController;

    @Test
    @DisplayName("validateDiff 호출 시 200 OK와 DiffResult를 반환한다")
    void validateDiff_returns200OK() {
        // given
        DiffResult diffResult = new DiffResult(100, 95, List.of(), List.of());
        given(elasticsearchSyncService.validateDiff()).willReturn(diffResult);

        // when
        ResponseEntity<DiffResult> response = elasticsearchSyncController.validateDiff();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(diffResult);
    }

    @Test
    @DisplayName("syncDiff 호출 시 200 OK와 SyncResult를 반환한다")
    void syncDiff_returns200OK() {
        // given
        SyncResult syncResult = new SyncResult(5, 5, 0, List.of());
        given(elasticsearchSyncService.syncDiff()).willReturn(syncResult);

        // when
        ResponseEntity<SyncResult> response = elasticsearchSyncController.syncDiff();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(syncResult);
    }

    @Test
    @DisplayName("reindexAll 호출 시 202 Accepted 및 비동기 시작 응답을 반환한다")
    void reindexAll_returns202Accepted() {
        // when
        ResponseEntity<Map<String, String>> response = elasticsearchSyncController.reindexAll();

        // then
        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("STARTED");
    }
}
