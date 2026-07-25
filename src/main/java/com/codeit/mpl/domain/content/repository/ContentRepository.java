package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.Content;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentRepository
        extends JpaRepository<Content, UUID>,
        JpaSpecificationExecutor<Content>,
        ContentQueryRepository {

    /**
     * 외부 API 출처와 외부 콘텐츠 ID로 콘텐츠를 조회합니다.
     */
    Optional<Content> findBySourceTypeAndExternalId(
            String sourceType,
            String externalId
    );

    /**
     * 외부 API 출처와 외부 콘텐츠 ID에 해당하는 콘텐츠가
     * 이미 저장되어 있는지 확인합니다.
     */
    boolean existsBySourceTypeAndExternalId(
            String sourceType,
            String externalId
    );

    /**
     * 외부 API 출처와 여러 외부 콘텐츠 ID를 기준으로
     * 저장된 콘텐츠 목록을 조회합니다.
     */
    List<Content> findAllBySourceTypeAndExternalIdIn(
            String sourceType,
            Collection<String> externalIds
    );

    /**
     * 콘텐츠의 누적 시청 횟수를 delta만큼 증가시킵니다.
     *
     * @param contentId 누적 시청 횟수를 증가시킬 콘텐츠 ID
     * @param delta 증가시킬 시청 횟수
     * @return 수정된 행 개수. 콘텐츠가 존재하면 1, 없으면 0
     */
    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query(
            value = """
                    UPDATE contents
                    SET watcher_count =
                        COALESCE(watcher_count, 0) + :delta
                    WHERE id = :contentId
                    """,
            nativeQuery = true
    )
    int addWatcherCount(
            @Param("contentId") UUID contentId,
            @Param("delta") long delta
    );

    @Query("SELECT c.id FROM Content c")
    List<UUID> findAllIds();
}