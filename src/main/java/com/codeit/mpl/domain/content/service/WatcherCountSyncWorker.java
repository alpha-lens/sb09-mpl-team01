package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.dto.response.ContentDto;
import com.codeit.mpl.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatcherCountSyncWorker {
    private final ContentRepository contentRepository;
    private final ContentService contentService;

    // 외부 연산(Redis, Cache)을 배제하고 오직 순수한 DB 작업만 원자적으로 처리
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ContentDto syncDbOnly(UUID contentId, long delta) {
        contentRepository.addWatcherCount(contentId, delta);
        return contentService.getContentNoCache(contentId);
    }
}
