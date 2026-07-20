package com.codeit.mpl.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codeit.mpl.domain.content.dto.response.ContentBatchLaunchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("removal")
class ContentBatchControllerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job initialContentCollectionJob;

    @Mock
    private Job dailyContentCollectionJob;

    private ContentBatchController contentBatchController;

    @BeforeEach
    void setUp() {
        contentBatchController =
                new ContentBatchController(
                        jobLauncher,
                        initialContentCollectionJob,
                        dailyContentCollectionJob
                );
    }

    @Test
    @DisplayName("초기 콘텐츠 수집 Job을 실행하고 202 응답을 반환한다")
    void runInitialContentCollection_success()
            throws Exception {

        // given
        JobExecution execution =
                createJobExecution(
                        "initialContentCollectionJob",
                        100L,
                        BatchStatus.STARTED
                );

        ArgumentCaptor<JobParameters> parametersCaptor =
                ArgumentCaptor.forClass(
                        JobParameters.class
                );

        when(
                jobLauncher.run(
                        eq(initialContentCollectionJob),
                        parametersCaptor.capture()
                )
        ).thenReturn(execution);

        // when
        ResponseEntity<ContentBatchLaunchResponse> response =
                contentBatchController
                        .runInitialContentCollection();

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().jobName())
                .isEqualTo(
                        "initialContentCollectionJob"
                );

        assertThat(response.getBody().executionId())
                .isEqualTo(100L);

        assertThat(response.getBody().status())
                .isEqualTo("STARTED");

        JobParameters capturedParameters =
                parametersCaptor.getValue();

        assertThat(
                capturedParameters
                        .getString("trigger")
        ).isEqualTo("MANUAL_INITIAL");

        assertThat(
                capturedParameters
                        .getLong("requestedAt")
        ).isNotNull();

        verify(jobLauncher).run(
                eq(initialContentCollectionJob),
                eq(capturedParameters)
        );
    }

    @Test
    @DisplayName("일일 콘텐츠 수집 Job을 실행하고 202 응답을 반환한다")
    void runDailyContentCollection_success()
            throws Exception {

        // given
        JobExecution execution =
                createJobExecution(
                        "dailyContentCollectionJob",
                        200L,
                        BatchStatus.COMPLETED
                );

        ArgumentCaptor<JobParameters> parametersCaptor =
                ArgumentCaptor.forClass(
                        JobParameters.class
                );

        when(
                jobLauncher.run(
                        eq(dailyContentCollectionJob),
                        parametersCaptor.capture()
                )
        ).thenReturn(execution);

        // when
        ResponseEntity<ContentBatchLaunchResponse> response =
                contentBatchController
                        .runDailyContentCollection();

        // then
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.ACCEPTED);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().jobName())
                .isEqualTo(
                        "dailyContentCollectionJob"
                );

        assertThat(response.getBody().executionId())
                .isEqualTo(200L);

        assertThat(response.getBody().status())
                .isEqualTo("COMPLETED");

        JobParameters capturedParameters =
                parametersCaptor.getValue();

        assertThat(
                capturedParameters
                        .getString("trigger")
        ).isEqualTo("MANUAL_DAILY");

        assertThat(
                capturedParameters
                        .getLong("requestedAt")
        ).isNotNull();

        verify(jobLauncher).run(
                eq(dailyContentCollectionJob),
                eq(capturedParameters)
        );
    }

    /**
     * Controller가 사용하는 JobExecution 응답값만 Mock으로 구성합니다.
     */
    private JobExecution createJobExecution(
            String jobName,
            Long executionId,
            BatchStatus status
    ) {
        JobExecution execution =
                mock(JobExecution.class);

        JobInstance jobInstance =
                mock(JobInstance.class);

        when(jobInstance.getJobName())
                .thenReturn(jobName);

        when(execution.getJobInstance())
                .thenReturn(jobInstance);

        when(execution.getId())
                .thenReturn(executionId);

        when(execution.getStatus())
                .thenReturn(status);

        return execution;
    }
}