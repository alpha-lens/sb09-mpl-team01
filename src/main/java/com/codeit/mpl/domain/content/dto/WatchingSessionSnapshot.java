package com.codeit.mpl.domain.content.dto;

import java.util.List;

public record WatchingSessionSnapshot(
    List<WatchingSessionDto> watchers,
    long totalCount
) {}
