package com.codeit.mpl.domain.content.dto.response;

public record ContentBatchLaunchResponse(
        String jobName,
        Long executionId,
        String status
) {
}