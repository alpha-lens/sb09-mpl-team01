package com.codeit.mpl.domain.content.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@SuppressWarnings("removal")
@Component
@ConditionalOnProperty(
        name = "content.collection.scheduler.enabled",
        havingValue = "true"
)
public class ContentCollectionScheduler {

    private static final String TRIGGER = "SCHEDULER";

    private final JobLauncher jobLauncher;
    private final Job dailyContentCollectionJob;

    public ContentCollectionScheduler(
            JobLauncher jobLauncher,
            @Qualifier("dailyContentCollectionJob")
            Job dailyContentCollectionJob
    ) {
        this.jobLauncher = jobLauncher;
        this.dailyContentCollectionJob =
                dailyContentCollectionJob;
    }

    @Scheduled(
            cron = "${content.collection.scheduler.cron}",
            zone = "${content.collection.scheduler.zone}"
    )
    public void runDailyContentCollectionJob()
            throws Exception {

        long startedAt = System.currentTimeMillis();

        log.info(
                "[Content Batch] 일일 콘텐츠 동기화 시작: jobName={}, trigger={}",
                dailyContentCollectionJob.getName(),
                TRIGGER
        );

        try {
            JobExecution execution = jobLauncher.run(
                    dailyContentCollectionJob,
                    new JobParametersBuilder()
                            .addLong(
                                    "requestedAt",
                                    startedAt
                            )
                            .addString(
                                    "trigger",
                                    TRIGGER
                            )
                            .toJobParameters()
            );

            log.info(
                    "[Content Batch] 일일 콘텐츠 동기화 실행 결과: "
                            + "jobName={}, executionId={}, status={}, "
                            + "trigger={}, durationMs={}",
                    execution.getJobInstance().getJobName(),
                    execution.getId(),
                    execution.getStatus(),
                    TRIGGER,
                    System.currentTimeMillis() - startedAt
            );
        } catch (Exception e) {
            log.error(
                    "[Content Batch] 일일 콘텐츠 동기화 실행 실패: "
                            + "jobName={}, trigger={}, durationMs={}",
                    dailyContentCollectionJob.getName(),
                    TRIGGER,
                    System.currentTimeMillis() - startedAt,
                    e
            );

            throw e;
        }
    }
}