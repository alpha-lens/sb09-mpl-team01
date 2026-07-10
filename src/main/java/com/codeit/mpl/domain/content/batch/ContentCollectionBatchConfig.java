package com.codeit.mpl.domain.content.batch;

import com.codeit.mpl.domain.content.service.SportsContentCollectionService;
import com.codeit.mpl.domain.content.service.TmdbContentCollectionService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ContentCollectionBatchConfig {

    /*
     * 서비스 최초 구축 시 한 번 실행
     */
    @Bean
    public Job initialContentCollectionJob(
            JobRepository jobRepository,
            Step initialTmdbMovieCollectionStep,
            Step initialTmdbTvCollectionStep,
            Step initialSportsCollectionStep
    ) {
        return new JobBuilder(
                "initialContentCollectionJob",
                jobRepository
        )
                .start(initialTmdbMovieCollectionStep)
                .next(initialTmdbTvCollectionStep)
                .next(initialSportsCollectionStep)
                .build();
    }

    /*
     * 매일 새벽 3시 실행
     */
    @Bean
    public Job dailyContentCollectionJob(
            JobRepository jobRepository,
            Step dailyTmdbMovieCollectionStep,
            Step dailyTmdbTvCollectionStep,
            Step dailySportsCollectionStep
    ) {
        return new JobBuilder(
                "dailyContentCollectionJob",
                jobRepository
        )
                .start(dailyTmdbMovieCollectionStep)
                .next(dailyTmdbTvCollectionStep)
                .next(dailySportsCollectionStep)
                .build();
    }

    @Bean
    public Step initialTmdbMovieCollectionStep(
            JobRepository jobRepository,
            TmdbContentCollectionService service
    ) {
        return new StepBuilder(
                "initialTmdbMovieCollectionStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    service.collectInitialMovies();
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step initialTmdbTvCollectionStep(
            JobRepository jobRepository,
            TmdbContentCollectionService service
    ) {
        return new StepBuilder(
                "initialTmdbTvCollectionStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    service.collectInitialTvSeries();
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step initialSportsCollectionStep(
            JobRepository jobRepository,
            SportsContentCollectionService service
    ) {
        return new StepBuilder(
                "initialSportsCollectionStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    service.collectUpcomingEvents();
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step dailyTmdbMovieCollectionStep(
            JobRepository jobRepository,
            TmdbContentCollectionService service
    ) {
        return new StepBuilder(
                "dailyTmdbMovieCollectionStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    service.collectDailyMovies();
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step dailyTmdbTvCollectionStep(
            JobRepository jobRepository,
            TmdbContentCollectionService service
    ) {
        return new StepBuilder(
                "dailyTmdbTvCollectionStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    service.collectDailyTvSeries();
                    return RepeatStatus.FINISHED;
                })
                .build();
    }

    @Bean
    public Step dailySportsCollectionStep(
            JobRepository jobRepository,
            SportsContentCollectionService service
    ) {
        return new StepBuilder(
                "dailySportsCollectionStep",
                jobRepository
        )
                .tasklet((contribution, chunkContext) -> {
                    service.collectUpcomingEvents();
                    return RepeatStatus.FINISHED;
                })
                .build();
    }
}