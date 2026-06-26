package com.codeit.mpl.domain.content.dto;

public record WatchingSessionChange(
    ChangeType type,
    WatchingSessionDto watchingSession,
    long watcherCount
) {}
