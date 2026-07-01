package com.codeit.mpl.domain.content.batch;

import com.codeit.mpl.domain.content.dto.request.ContentImportRequest;
import com.codeit.mpl.domain.content.dto.response.ExternalContentSearchResult;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.service.ContentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class ContentCollectionBatchConfig {

    private final ContentService contentService;

    @Value("${admin.email}")
    private String adminEmail;

    @Bean
    public Job contentCollectionJob(
            JobRepository jobRepository,
            Step tmdbMovieCollectionStep,
            Step tmdbTvSeriesCollectionStep,
            Step sportsCollectionStep
    ) {
        return new JobBuilder("contentCollectionJob", jobRepository)
                .start(tmdbMovieCollectionStep)
                .next(tmdbTvSeriesCollectionStep)
                .next(sportsCollectionStep)
                .build();
    }

    @Bean
    public Step tmdbMovieCollectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder("tmdbMovieCollectionStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    collectContents(ContentType.MOVIE, List.of("Interstellar", "Inception", "Dune"));
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step tmdbTvSeriesCollectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder("tmdbTvSeriesCollectionStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    collectContents(ContentType.TVSERIES, List.of("Breaking Bad", "Stranger Things", "Squid Game"));
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step sportsCollectionStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager
    ) {
        return new StepBuilder("sportsCollectionStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    collectContents(ContentType.SPORT, List.of("Arsenal", "Barcelona", "Manchester United"));
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    private void collectContents(ContentType type, List<String> keywords) {
        for (String keyword : keywords) {
            List<ExternalContentSearchResult> results =
                    contentService.searchExternalContents(keyword, type);

            results.stream()
                    .limit(3)
                    .forEach(result -> contentService.importExternalContent(
                            adminEmail,
                            new ContentImportRequest(
                                    result.externalId(),
                                    type
                            )
                    ));
        }
    }
}