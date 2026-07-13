package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContentRepository
        extends JpaRepository<Content, UUID>,
        JpaSpecificationExecutor<Content>,
        ContentQueryRepository {

    Optional<Content> findBySourceTypeAndExternalId(
            String sourceType,
            String externalId
    );

    boolean existsBySourceTypeAndExternalId(
            String sourceType,
            String externalId
    );

    /**
     * 배치에서 기존 외부 콘텐츠를 한 번에 조회합니다.
     */
    List<Content> findAllBySourceTypeAndExternalIdIn(
            String sourceType,
            Collection<String> externalIds
    );
}
