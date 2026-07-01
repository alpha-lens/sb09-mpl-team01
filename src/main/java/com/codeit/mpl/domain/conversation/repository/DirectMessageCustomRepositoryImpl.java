package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import com.codeit.mpl.domain.conversation.entity.QDirectMessage;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class DirectMessageCustomRepositoryImpl implements DirectMessageCustomRepository {

    private final JPAQueryFactory queryFactory;
    private final QDirectMessage dm = QDirectMessage.directMessage;

    @Override
    public List<DirectMessage> findMessages(UUID conversationId, UUID idAfter, Pageable pageable) {
        return queryFactory.selectFrom(dm)
            .join(dm.conversation).fetchJoin()
            .join(dm.sender).fetchJoin()
            .join(dm.receiver).fetchJoin()
            .where(
                dm.conversation.id.eq(conversationId),
                cursorCondition(idAfter)
            )
            .orderBy(dm.createdAt.desc(), dm.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
    }

    private BooleanExpression cursorCondition(UUID idAfter) {
        if (idAfter == null) {
            return null;
        }
        DirectMessage target = queryFactory.selectFrom(dm)
            .where(dm.id.eq(idAfter))
            .fetchOne();
        if (target == null) {
            return null;
        }
        Instant cursorTime = target.getCreatedAt();
        return dm.createdAt.lt(cursorTime)
            .or(dm.createdAt.eq(cursorTime).and(dm.id.lt(idAfter)));
    }
}
