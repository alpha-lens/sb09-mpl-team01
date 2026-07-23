package com.codeit.mpl.domain.content.event;

import com.codeit.mpl.domain.content.entity.ContentDocument;
import com.codeit.mpl.domain.content.repository.ContentSearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContentEventListener {

    private final ContentSearchRepository contentSearchRepository;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handleContentEvent(
            ContentEvent event
    ) {
        try {
            switch (event.getEventType()) {
                case CREATED, UPDATED -> {
                    ContentDocument document =
                            ContentDocument.from(
                                    event.getContent()
                            );

                    contentSearchRepository.save(
                            document
                    );

                    log.info(
                            "[ES Sync] 콘텐츠 색인 완료: "
                                    + "contentId={}, eventType={}",
                            document.getId(),
                            event.getEventType()
                    );
                }

                case DELETED -> {
                    String contentId =
                            event.getContent()
                                    .getId()
                                    .toString();

                    contentSearchRepository.deleteById(
                            contentId
                    );

                    log.info(
                            "[ES Sync] 콘텐츠 삭제 완료: "
                                    + "contentId={}, eventType={}",
                            contentId,
                            event.getEventType()
                    );
                }
            }
        } catch (Exception e) {
            log.error(
                    "[ES Sync] 콘텐츠 동기화 실패: "
                            + "contentId={}, eventType={}",
                    event.getContent().getId(),
                    event.getEventType(),
                    e
            );
        }
    }
}