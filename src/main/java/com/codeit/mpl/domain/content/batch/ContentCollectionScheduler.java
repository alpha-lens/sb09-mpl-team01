package com.codeit.mpl.domain.content.batch;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@SuppressWarnings("removal")
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "content.collection.scheduler.enabled",
        havingValue = "true"
)
public class ContentCollectionScheduler {

    private final JobLauncher jobLauncher;
    private final Job contentCollectionJob;

    @Scheduled(
            cron = "${content.collection.scheduler.cron}",
            zone = "${content.collection.scheduler.zone}"
    )
    public void runContentCollectionJob() throws Exception {
        jobLauncher.run(
                contentCollectionJob,
                new JobParametersBuilder()
                        .addString("requestedAt", LocalDateTime.now().toString())
                        .toJobParameters()
        );
    }
}