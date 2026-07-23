package com.codeit.mpl.domain.content.controller;

import com.codeit.mpl.domain.content.dto.response.ContentBatchLaunchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@SuppressWarnings("removal")
@RestController
@RequestMapping("/api/admin/content-batches")
public class ContentBatchController {

    private static final String MANUAL_INITIAL =
            "MANUAL_INITIAL";

    private static final String MANUAL_DAILY =
            "MANUAL_DAILY";

    private final JobLauncher jobLauncher;
    private final Job initialContentCollectionJob;
    private final Job dailyContentCollectionJob;

    public ContentBatchController(
            JobLauncher jobLauncher,
            @Qualifier("initialContentCollectionJob")
            Job initialContentCollectionJob,
            @Qualifier("dailyContentCollectionJob")
            Job dailyContentCollectionJob
    ) {
        this.jobLauncher = jobLauncher;
        this.initialContentCollectionJob =
                initialContentCollectionJob;
        this.dailyContentCollectionJob =
                dailyContentCollectionJob;
    }

    /*
     * 서비스 최초 구축용
     *
     * POST /api/admin/content-batches/initial
     */
    @PostMapping("/initial")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContentBatchLaunchResponse>
    runInitialContentCollection() throws Exception {

        JobExecution execution = launch(
                initialContentCollectionJob,
                MANUAL_INITIAL
        );

        return ResponseEntity.accepted().body(
                toResponse(execution)
        );
    }

    /*
     * 새벽 3시 배치를 즉시 시험할 때 사용
     *
     * POST /api/admin/content-batches/daily
     */
    @PostMapping("/daily")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContentBatchLaunchResponse>
    runDailyContentCollection() throws Exception {

        JobExecution execution = launch(
                dailyContentCollectionJob,
                MANUAL_DAILY
        );

        return ResponseEntity.accepted().body(
                toResponse(execution)
        );
    }

    private JobExecution launch(
            Job job,
            String trigger
    ) throws Exception {

        long startedAt = System.currentTimeMillis();

        log.info(
                "[Content Batch] 관리자 수동 실행 시작: "
                        + "jobName={}, trigger={}",
                job.getName(),
                trigger
        );

        try {
            JobExecution execution = jobLauncher.run(
                    job,
                    new JobParametersBuilder()
                            .addLong(
                                    "requestedAt",
                                    startedAt
                            )
                            .addString(
                                    "trigger",
                                    trigger
                            )
                            .toJobParameters()
            );

            log.info(
                    "[Content Batch] 관리자 수동 실행 결과: "
                            + "jobName={}, executionId={}, status={}, "
                            + "trigger={}, durationMs={}",
                    execution.getJobInstance().getJobName(),
                    execution.getId(),
                    execution.getStatus(),
                    trigger,
                    System.currentTimeMillis() - startedAt
            );

            return execution;
        } catch (Exception e) {
            log.error(
                    "[Content Batch] 관리자 수동 실행 실패: "
                            + "jobName={}, trigger={}, durationMs={}",
                    job.getName(),
                    trigger,
                    System.currentTimeMillis() - startedAt,
                    e
            );

            throw e;
        }
    }

    private ContentBatchLaunchResponse toResponse(
            JobExecution execution
    ) {
        return new ContentBatchLaunchResponse(
                execution.getJobInstance().getJobName(),
                execution.getId(),
                execution.getStatus().name()
        );
    }
}