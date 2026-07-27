package com.codeit.mpl.domain.content.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContentCollectionSchedulerTest {

    @Mock
    private JobLauncher jobLauncher;

    @Mock
    private Job dailyContentCollectionJob;

    @InjectMocks
    private ContentCollectionScheduler scheduler;

    @Test
    @DisplayName("runDailyContentCollectionJob 실행 성공 시 JobLauncher를 구동한다")
    void runDailyContentCollectionJob_Success() throws Exception {
        given(dailyContentCollectionJob.getName()).willReturn("dailyContentCollectionJob");

        JobExecution execution = mock(JobExecution.class);
        JobInstance instance = mock(JobInstance.class);
        given(instance.getJobName()).willReturn("dailyContentCollectionJob");
        given(execution.getJobInstance()).willReturn(instance);
        given(execution.getId()).willReturn(1L);
        given(execution.getStatus()).willReturn(BatchStatus.COMPLETED);

        given(jobLauncher.run(eq(dailyContentCollectionJob), any(JobParameters.class)))
                .willReturn(execution);

        scheduler.runDailyContentCollectionJob();

        verify(jobLauncher).run(eq(dailyContentCollectionJob), any(JobParameters.class));
    }

    @Test
    @DisplayName("runDailyContentCollectionJob 실행 실패 시 예외를 다시 던진다")
    void runDailyContentCollectionJob_Failure() throws Exception {
        given(dailyContentCollectionJob.getName()).willReturn("dailyContentCollectionJob");
        given(jobLauncher.run(eq(dailyContentCollectionJob), any(JobParameters.class)))
                .willThrow(new RuntimeException("Batch launch failed"));

        assertThatThrownBy(() -> scheduler.runDailyContentCollectionJob())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Batch launch failed");
    }
}
