package com.codeit.mpl.domain.content.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
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

        log.info("일일 콘텐츠 동기화 배치 시작");

        jobLauncher.run(
                dailyContentCollectionJob,
                new JobParametersBuilder()
                        .addLong(
                                "requestedAt",
                                System.currentTimeMillis()
                        )
                        .addString(
                                "trigger",
                                "SCHEDULER"
                        )
                        .toJobParameters()
        );
    }
}