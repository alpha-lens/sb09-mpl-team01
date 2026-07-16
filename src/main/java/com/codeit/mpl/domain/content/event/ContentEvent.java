package com.codeit.mpl.domain.content.event;

import com.codeit.mpl.domain.content.entity.Content;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ContentEvent {
    public enum EventType { CREATED, UPDATED, DELETED }
    private final Content content;
    private final EventType eventType;
}
