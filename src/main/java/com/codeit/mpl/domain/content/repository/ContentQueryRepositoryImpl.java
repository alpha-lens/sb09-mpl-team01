package com.codeit.mpl.domain.content.repository;

import static com.codeit.mpl.domain.content.entity.QContent.content;
import static com.codeit.mpl.domain.review.entity.QReview.review;

import com.codeit.mpl.domain.content.dto.query.ContentQueryRow;
import com.codeit.mpl.domain.content.entity.Content;
import com.codeit.mpl.domain.content.entity.ContentType;
import com.codeit.mpl.infra.common.dto.Direction;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContentQueryRepositoryImpl
        implements ContentQueryRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<ContentQueryRow> findContents(
            String cursor,
            UUID idAfter,
            String keywordLike,
            List<UUID> matchingIds,
            ContentType type,
            int limit,
            String sortBy,
            Direction sortDirection
    ) {
        NumberExpression<Double> averageRating =
                review.rating
                        .avg()
                        .coalesce(0.0);

        NumberExpression<Long> reviewCount =
                review.id
                        .countDistinct();

        /*
         * 현재 활성 시청 세션 개수가 아니라
         * Content에 저장된 누적 시청 횟수를 사용합니다.
         */
        NumberExpression<Long> watcherCount =
                content.watcherCount
                        .coalesce(0L);

        BooleanBuilder where =
                createFilterCondition(
                        keywordLike,
                        matchingIds,
                        type
                );

        BooleanExpression havingCursorCondition =
                null;

        if (cursor != null
                && !cursor.isBlank()
                && idAfter != null) {

            CursorAnchor anchor =
                    loadAndValidateAnchor(
                            cursor,
                            idAfter,
                            keywordLike,
                            type,
                            sortBy
                    );

            if ("createdAt".equals(sortBy)) {
                where.and(
                        createCreatedAtCursorCondition(
                                anchor,
                                sortDirection
                        )
                );

            } else {
                havingCursorCondition =
                        createCalculatedCursorCondition(
                                anchor,
                                sortBy,
                                sortDirection,
                                averageRating,
                                watcherCount
                        );
            }
        }

        JPAQuery<Tuple> query =
                queryFactory
                        .select(
                                content.id,
                                content.createdAt,
                                averageRating,
                                reviewCount,
                                watcherCount
                        )
                        .from(content)
                        .leftJoin(review)
                        .on(
                                review.content.eq(
                                        content
                                )
                        )
                        .where(where)
                        .groupBy(
                                content.id,
                                content.createdAt,
                                content.watcherCount
                        );

        if (havingCursorCondition != null) {
            query.having(
                    havingCursorCondition
            );
        }

        query.orderBy(
                createOrderSpecifiers(
                        sortBy,
                        sortDirection,
                        averageRating,
                        watcherCount
                )
        );

        List<Tuple> tuples =
                query.limit(
                                limit + 1L
                        )
                        .fetch();

        if (tuples.isEmpty()) {
            return List.of();
        }

        List<UUID> contentIds =
                tuples.stream()
                        .map(tuple ->
                                tuple.get(
                                        content.id
                                )
                        )
                        .toList();

        /*
         * 집계 쿼리에서는 Content 전체 엔티티 대신
         * 콘텐츠 ID와 집계값만 조회합니다.
         *
         * 이후 현재 페이지에 포함된 콘텐츠 엔티티만
         * 한 번의 추가 쿼리로 조회합니다.
         */
        Map<UUID, Content> contentById =
                new LinkedHashMap<>();

        queryFactory
                .selectFrom(content)
                .leftJoin(content.tags).fetchJoin()
                .where(
                        content.id.in(
                                contentIds
                        )
                )
                .fetch()
                .forEach(item ->
                        contentById.put(
                                item.getId(),
                                item
                        )
                );

        List<ContentQueryRow> rows =
                new ArrayList<>(
                        tuples.size()
                );

        for (Tuple tuple : tuples) {
            UUID contentId =
                    tuple.get(
                            content.id
                    );

            Content item =
                    contentById.get(
                            contentId
                    );

            if (item == null) {
                continue;
            }

            Double rowAverageRating =
                    defaultDouble(
                            tuple.get(
                                    averageRating
                            )
                    );

            Long rowReviewCount =
                    defaultLong(
                            tuple.get(
                                    reviewCount
                            )
                    );

            Long rowWatcherCount =
                    defaultLong(
                            tuple.get(
                                    watcherCount
                            )
                    );

            rows.add(
                    new ContentQueryRow(
                            item,
                            rowAverageRating,
                            Math.toIntExact(
                                    rowReviewCount
                            ),
                            rowWatcherCount
                    )
            );
        }

        return rows;
    }

    @Override
    public long countContents(
            String keywordLike,
            List<UUID> matchingIds,
            ContentType type
    ) {
        Long count =
                queryFactory
                        .select(
                                content.count()
                        )
                        .from(content)
                        .where(
                                createFilterCondition(
                                        keywordLike,
                                        matchingIds,
                                        type
                                )
                        )
                        .fetchOne();

        return count == null
                ? 0L
                : count;
    }

    /**
     * ES matchingIds 필터, 제목밀설명 키워드 및 콘텐츠 타입 필터를 생성합니다.
     *
     * @param matchingIds ES 검색으로 필터링된 ID 목록. null이면 적용 안 함.
     */
    private BooleanBuilder createFilterCondition(
            String keywordLike,
            List<UUID> matchingIds,
            ContentType type
    ) {
        BooleanBuilder builder =
                new BooleanBuilder();

        // ES 검색 ID 필터
        if (matchingIds != null
                && !matchingIds.isEmpty()) {
            builder.and(
                    content.id.in(
                            matchingIds
                    )
            );
        }

        if (keywordLike != null
                && !keywordLike.isBlank()) {

            String normalizedKeyword =
                    keywordLike
                            .trim()
                            .toLowerCase();

            BooleanExpression keywordCondition =
                    content.title
                            .lower()
                            .contains(
                                    normalizedKeyword
                            );

            /*
             * description은 nullable이므로
             * null이 아닌 경우에만 lower/contains 조건을 적용합니다.
             */
            BooleanExpression descriptionCondition =
                    content.description
                            .isNotNull()
                            .and(
                                    content.description
                                            .lower()
                                            .contains(
                                                    normalizedKeyword
                                            )
                            );

            builder.and(
                    keywordCondition.or(
                            descriptionCondition
                    )
            );
        }

        if (type != null) {
            builder.and(
                    content.type.eq(
                            type
                    )
            );
        }

        return builder;
    }

    /**
     * idAfter가 가리키는 콘텐츠의 실제 정렬값을 조회하고
     * 요청 cursor 값과 일치하는지 검증합니다.
     */
    private CursorAnchor loadAndValidateAnchor(
            String cursor,
            UUID idAfter,
            String keywordLike,
            ContentType type,
            String sortBy
    ) {
        NumberExpression<Double> averageRating =
                review.rating
                        .avg()
                        .coalesce(0.0);

        NumberExpression<Long> watcherCount =
                content.watcherCount
                        .coalesce(0L);

        BooleanBuilder where =
                createFilterCondition(
                        keywordLike,
                        null,
                        type
                );

        where.and(
                content.id.eq(
                        idAfter
                )
        );

        Tuple tuple =
                queryFactory
                        .select(
                                content.id,
                                content.createdAt,
                                averageRating,
                                watcherCount
                        )
                        .from(content)
                        .leftJoin(review)
                        .on(
                                review.content.eq(
                                        content
                                )
                        )
                        .where(where)
                        .groupBy(
                                content.id,
                                content.createdAt,
                                content.watcherCount
                        )
                        .fetchOne();

        if (tuple == null) {
            throw new IllegalArgumentException(
                    "idAfter가 현재 조회 조건에 해당하는 콘텐츠를 가리키지 않습니다."
            );
        }

        Instant createdAt =
                tuple.get(
                        content.createdAt
                );

        Double anchorAverageRating =
                defaultDouble(
                        tuple.get(
                                averageRating
                        )
                );

        Long anchorWatcherCount =
                defaultLong(
                        tuple.get(
                                watcherCount
                        )
                );

        CursorAnchor anchor =
                new CursorAnchor(
                        idAfter,
                        createdAt,
                        anchorWatcherCount,
                        anchorAverageRating
                );

        validateCursorValue(
                cursor,
                sortBy,
                anchor
        );

        return anchor;
    }

    /**
     * 전달된 cursor 값이 idAfter 콘텐츠의 실제 정렬값과
     * 일치하는지 검증합니다.
     */
    private void validateCursorValue(
            String cursor,
            String sortBy,
            CursorAnchor anchor
    ) {
        switch (sortBy) {
            case "createdAt" -> {
                Instant cursorValue =
                        parseInstantCursor(
                                cursor
                        );

                if (!cursorValue.equals(
                        anchor.createdAt()
                )) {
                    throw new IllegalArgumentException(
                            "cursor와 idAfter가 현재 createdAt 정렬 결과와 일치하지 않습니다."
                    );
                }
            }

            case "watcherCount" -> {
                long cursorValue =
                        parseLongCursor(
                                cursor
                        );

                if (cursorValue
                        != anchor.watcherCount()) {

                    throw new IllegalArgumentException(
                            "cursor와 idAfter가 현재 watcherCount 정렬 결과와 일치하지 않습니다."
                    );
                }
            }

            case "rate" -> {
                double cursorValue =
                        parseDoubleCursor(
                                cursor
                        );

                if (Double.compare(
                        cursorValue,
                        anchor.averageRating()
                ) != 0) {

                    throw new IllegalArgumentException(
                            "cursor와 idAfter가 현재 rate 정렬 결과와 일치하지 않습니다."
                    );
                }
            }

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 정렬 기준입니다."
                    );
        }
    }

    /**
     * createdAt 정렬의 다음 페이지 조회 조건입니다.
     *
     * 정렬값이 같을 경우 id를 보조 정렬 기준으로 사용합니다.
     */
    private BooleanExpression
    createCreatedAtCursorCondition(
            CursorAnchor anchor,
            Direction sortDirection
    ) {
        if (sortDirection
                == Direction.ASCENDING) {

            return content.createdAt
                    .gt(
                            anchor.createdAt()
                    )
                    .or(
                            content.createdAt
                                    .eq(
                                            anchor.createdAt()
                                    )
                                    .and(
                                            content.id.gt(
                                                    anchor.id()
                                            )
                                    )
                    );
        }

        return content.createdAt
                .lt(
                        anchor.createdAt()
                )
                .or(
                        content.createdAt
                                .eq(
                                        anchor.createdAt()
                                )
                                .and(
                                        content.id.lt(
                                                anchor.id()
                                        )
                                )
                );
    }

    /**
     * watcherCount 또는 rate 정렬의 다음 페이지 조회 조건입니다.
     *
     * 정렬 우선순위:
     * 1. watcherCount 또는 평균 평점
     * 2. createdAt
     * 3. id
     */
    private BooleanExpression
    createCalculatedCursorCondition(
            CursorAnchor anchor,
            String sortBy,
            Direction sortDirection,
            NumberExpression<Double> averageRating,
            NumberExpression<Long> watcherCount
    ) {
        BooleanExpression secondaryCondition =
                createSecondaryCursorCondition(
                        anchor,
                        sortDirection
                );

        if ("watcherCount".equals(
                sortBy
        )) {
            if (sortDirection
                    == Direction.ASCENDING) {

                return watcherCount
                        .gt(
                                anchor.watcherCount()
                        )
                        .or(
                                watcherCount
                                        .eq(
                                                anchor.watcherCount()
                                        )
                                        .and(
                                                secondaryCondition
                                        )
                        );
            }

            return watcherCount
                    .lt(
                            anchor.watcherCount()
                    )
                    .or(
                            watcherCount
                                    .eq(
                                            anchor.watcherCount()
                                    )
                                    .and(
                                            secondaryCondition
                                    )
                    );
        }

        if ("rate".equals(
                sortBy
        )) {
            if (sortDirection
                    == Direction.ASCENDING) {

                return averageRating
                        .gt(
                                anchor.averageRating()
                        )
                        .or(
                                averageRating
                                        .eq(
                                                anchor.averageRating()
                                        )
                                        .and(
                                                secondaryCondition
                                        )
                        );
            }

            return averageRating
                    .lt(
                            anchor.averageRating()
                    )
                    .or(
                            averageRating
                                    .eq(
                                            anchor.averageRating()
                                    )
                                    .and(
                                            secondaryCondition
                                    )
                    );
        }

        throw new IllegalArgumentException(
                "계산 정렬은 watcherCount 또는 rate만 지원합니다."
        );
    }

    /**
     * 정렬값이 같은 콘텐츠의 다음 페이지 조건입니다.
     *
     * createdAt과 id를 순서대로 비교합니다.
     */
    private BooleanExpression
    createSecondaryCursorCondition(
            CursorAnchor anchor,
            Direction sortDirection
    ) {
        if (sortDirection
                == Direction.ASCENDING) {

            return content.createdAt
                    .gt(
                            anchor.createdAt()
                    )
                    .or(
                            content.createdAt
                                    .eq(
                                            anchor.createdAt()
                                    )
                                    .and(
                                            content.id.gt(
                                                    anchor.id()
                                            )
                                    )
                    );
        }

        return content.createdAt
                .lt(
                        anchor.createdAt()
                )
                .or(
                        content.createdAt
                                .eq(
                                        anchor.createdAt()
                                )
                                .and(
                                        content.id.lt(
                                                anchor.id()
                                        )
                                )
                );
    }

    /**
     * 요청된 정렬 기준과 방향에 맞는 정렬 조건을 생성합니다.
     */
    private OrderSpecifier<?>[]
    createOrderSpecifiers(
            String sortBy,
            Direction sortDirection,
            NumberExpression<Double> averageRating,
            NumberExpression<Long> watcherCount
    ) {
        boolean ascending =
                sortDirection
                        == Direction.ASCENDING;

        List<OrderSpecifier<?>> orders =
                new ArrayList<>();

        switch (sortBy) {
            case "createdAt" -> {
                /*
                 * createdAt은 아래 공통 정렬 부분에서
                 * 한 번만 추가합니다.
                 */
            }

            case "watcherCount" ->
                    orders.add(
                            ascending
                                    ? watcherCount.asc()
                                    : watcherCount.desc()
                    );

            case "rate" ->
                    orders.add(
                            ascending
                                    ? averageRating.asc()
                                    : averageRating.desc()
                    );

            default ->
                    throw new IllegalArgumentException(
                            "지원하지 않는 정렬 기준입니다."
                    );
        }

        orders.add(
                ascending
                        ? content.createdAt.asc()
                        : content.createdAt.desc()
        );

        orders.add(
                ascending
                        ? content.id.asc()
                        : content.id.desc()
        );

        return orders.toArray(
                new OrderSpecifier<?>[0]
        );
    }

    private Instant parseInstantCursor(
            String cursor
    ) {
        try {
            return Instant.parse(
                    cursor
            );

        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "createdAt 정렬 시 cursor는 올바른 Instant 형식이어야 합니다.",
                    exception
            );
        }
    }

    private long parseLongCursor(
            String cursor
    ) {
        try {
            return Long.parseLong(
                    cursor
            );

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "watcherCount 정렬 시 cursor는 숫자여야 합니다.",
                    exception
            );
        }
    }

    private double parseDoubleCursor(
            String cursor
    ) {
        try {
            return Double.parseDouble(
                    cursor
            );

        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "rate 정렬 시 cursor는 숫자여야 합니다.",
                    exception
            );
        }
    }

    private long defaultLong(
            Long value
    ) {
        return value == null
                ? 0L
                : value;
    }

    private double defaultDouble(
            Double value
    ) {
        return value == null
                ? 0.0
                : value;
    }

    private record CursorAnchor(
            UUID id,
            Instant createdAt,
            long watcherCount,
            double averageRating
    ) {
    }
}

