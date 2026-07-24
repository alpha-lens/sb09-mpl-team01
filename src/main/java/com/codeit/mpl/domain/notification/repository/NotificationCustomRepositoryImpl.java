package com.codeit.mpl.domain.notification.repository;

import com.codeit.mpl.domain.notification.entity.Notification;
import com.codeit.mpl.domain.notification.entity.QNotification;
import com.codeit.mpl.infra.common.dto.Direction;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class NotificationCustomRepositoryImpl implements NotificationCustomRepository {

  private final JPAQueryFactory queryFactory;
  private final QNotification notification = QNotification.notification;

  @Override
  public List<Notification> findNotificationsWithCursor(
      UUID receiverId, Instant cursor, UUID idAfter, int limit, Direction sortDirection
  ) {
    boolean isAsc = Direction.ASCENDING.equals(sortDirection);

    return queryFactory
        .selectFrom(notification)
        .where(
            notification.receiver.id.eq(receiverId),
            getCursorCondition(cursor, idAfter, isAsc) // 복합 커서 조건 적용
        )
        .orderBy(
            getLogicallyOrder(isAsc),
            isAsc ? notification.id.asc() : notification.id.desc()
        )
        .limit(limit + 1) // 다음 페이지 여부 확인용 n+1 조회
        .fetch();
  }

  /**
   * 동적 정렬 방향에 따른 커서 조건절 생성 (복합 커서 완벽 방어)
   */
  private BooleanExpression getCursorCondition(Instant cursor, UUID idAfter, boolean isAsc) {
    if (cursor == null) {
      return null; // 첫 페이지 조회 시 조건 패스
    }

    if (isAsc) {
      // 오름차순(ASC): 기준 시간보다 뒤에 있거나, 시간이 같으면 ID가 큰 데이터 조회
      if (idAfter != null) {
        return notification.createdAt.gt(cursor)
            .or(notification.createdAt.eq(cursor).and(notification.id.gt(idAfter)));
      }
      return notification.createdAt.gt(cursor);
    } else {
      // 내림차순(DESC): 기준 시간보다 앞에 있거나, 시간이 같으면 ID가 작은 데이터 조회
      if (idAfter != null) {
        return notification.createdAt.lt(cursor)
            .or(notification.createdAt.eq(cursor).and(notification.id.lt(idAfter)));
      }
      return notification.createdAt.lt(cursor);
    }
  }

  /**
   * 동적 정렬 조건 생성
   */
  private OrderSpecifier<?> getLogicallyOrder(boolean isAsc) {
    return isAsc ? notification.createdAt.asc() : notification.createdAt.desc();
  }
}