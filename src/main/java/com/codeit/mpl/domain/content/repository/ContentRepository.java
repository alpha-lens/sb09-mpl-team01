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
     * 콘텐츠의 누적 시청 횟수를 1 증가시킵니다.
     *
     * 엔티티를 먼저 조회하고 값을 변경하는 방식이 아니라
     * 데이터베이스에서 직접 watcher_count 값을 증가시킵니다.
     *
     * 다음과 같은 동시성 문제를 줄일 수 있습니다.
     *
     * 예:
     * watcher_count가 10인 상태에서 두 사용자가 동시에 입장할 경우
     * 각각의 UPDATE가 순차적으로 반영되어 최종 값은 12가 됩니다.
     *
     * @param contentId 누적 시청 횟수를 증가시킬 콘텐츠 ID
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
                        COALESCE(watcher_count, 0) + 1
                    WHERE id = :contentId
                    """,
            nativeQuery = true
    )
    int incrementWatcherCount(
            @Param("contentId") UUID contentId
    );

    @Query("SELECT c.id FROM Content c")
    List<UUID> findAllIds();
}