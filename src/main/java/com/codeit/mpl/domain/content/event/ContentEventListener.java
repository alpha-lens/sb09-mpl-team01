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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleContentEvent(ContentEvent event) {
        try {
            switch (event.getEventType()) {
                case CREATED, UPDATED -> {
                    ContentDocument doc = ContentDocument.from(event.getContent());
                    contentSearchRepository.save(doc);
                    log.info("Successfully indexed content in ES: {}", doc.getId());
                }
                case DELETED -> {
                    String id = event.getContent().getId().toString();
                    contentSearchRepository.deleteById(id);
                    log.info("Successfully deleted content from ES: {}", id);
                }
            }
        } catch (Exception e) {
            log.error("Failed to sync content with Elasticsearch for contentId: {}", event.getContent().getId(), e);
        }
    }
}
