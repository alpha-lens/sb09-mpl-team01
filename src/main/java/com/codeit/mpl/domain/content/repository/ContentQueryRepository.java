package com.codeit.mpl.domain.content.repository;

import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.domain.content.dto.query.ContentQueryRow;
import com.codeit.mpl.infra.common.dto.Direction;
import java.util.List;
import java.util.UUID;

public interface ContentQueryRepository {

    /**
     * 콘텐츠 목록을 DB에서 집계·정렬·커서 페이지네이션하여 조회합니다.
     *
     * 지원 정렬:
     * - createdAt
     * - watcherCount
     * - rate
     */
    /**
     * ES 검색 결과 ID 목록을 기반으로 DB에서 커서 페이지네이션을 적용합니다.
     *
     * @param matchingIds ES 검색으로 필터링된 ID 목록. null이면 필터 없이 전체 조회.
     */
    List<ContentQueryRow> findContents(
            String cursor,
            UUID idAfter,
            String keywordLike,
            List<UUID> matchingIds,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    );

    /**
     * 키워드와 타입 필터가 적용된 전체 콘텐츠 개수를 조회합니다.
     *
     * @param matchingIds ES 검색으로 필터링된 ID 목록. null이면 필터 없이 전체 조회.
     */
    long countContents(
            String keywordLike,
            List<UUID> matchingIds,
            ContentType type
    );
}