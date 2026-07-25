package com.codeit.mpl.domain.content.service;

import com.codeit.mpl.domain.content.repository.ContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentPopularityService {

    private final ContentRepository contentRepository;

    /**
     * 콘텐츠의 누적 시청 횟수를 1 증가시킵니다.
     */
    @Transactional
    public void increaseWatcherCount(
            UUID contentId
    ) {
        int updatedCount =
                contentRepository.addWatcherCount(
                        contentId, 1L
                );

        if (updatedCount == 0) {
            throw new IllegalArgumentException(
                    "존재하지 않는 콘텐츠입니다: "
                            + contentId
            );
        }

        log.info(
                "[Content Popularity] Increased watcher count: contentId={}",
                contentId
        );
    }
}