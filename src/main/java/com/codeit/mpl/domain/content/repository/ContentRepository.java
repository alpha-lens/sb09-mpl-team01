package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ContentRepository extends JpaRepository<Content, UUID>, JpaSpecificationExecutor<Content> {

    Optional<Content> findBySourceTypeAndExternalId(
            String sourceType,
            String externalId
    );

    boolean existsBySourceTypeAndExternalId(
            String sourceType,
            String externalId
    );
}