package com.codeit.mpl.domain.content.dto.query;

import com.codeit.mpl.domain.content.entity.Content;

/**
 * QueryDSL 콘텐츠 목록 조회 결과를 담는 내부 DTO입니다.
 */
public record ContentQueryRow(
        Content content,
        Double averageRating,
        Integer reviewCount,
        Long watcherCount
) {
}