package com.codeit.mpl.domain.conversation.repository;

import com.codeit.mpl.domain.conversation.dto.ConversationQueryDto;
import com.codeit.mpl.domain.conversation.entity.QConversation;
import com.codeit.mpl.domain.conversation.entity.QDirectMessage;
import com.codeit.mpl.domain.user.entity.QUser;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
public class ConversationCustomRepositoryImpl implements ConversationCustomRepository {

    private final JPAQueryFactory queryFactory;
    private final QConversation conversation = QConversation.conversation;
    private final QDirectMessage dm = QDirectMessage.directMessage;
    private final QUser user1 = new QUser("user1");
    private final QUser user2 = new QUser("user2");

    @Override
    public List<ConversationQueryDto> findAllConversationsWithStats(
        UUID userId, String keywordLike, String cursor, UUID idAfter, int limit
    ) {
        QDirectMessage subDm = new QDirectMessage("subDm");
        QDirectMessage unreadDm = new QDirectMessage("unreadDm");

        return queryFactory.select(
            Projections.constructor(
                ConversationQueryDto.class,
                conversation.id,
                new CaseBuilder()
                    .when(conversation.user1.id.eq(userId)).then(conversation.user2.id)
                    .otherwise(conversation.user1.id),
                new CaseBuilder()
                    .when(conversation.user1.id.eq(userId)).then(conversation.user2.name)
                    .otherwise(conversation.user1.name),
                new CaseBuilder()
                    .when(conversation.user1.id.eq(userId)).then(conversation.user2.profileImageUrl)
                    .otherwise(conversation.user1.profileImageUrl),
                dm.id,
                dm.content,
                dm.createdAt,
                JPAExpressions.select(unreadDm.count())
                    .from(unreadDm)
                    .where(
                        unreadDm.conversation.id.eq(conversation.id),
                        unreadDm.receiver.id.eq(userId),
                        unreadDm.isRead.eq(false)
                    )
            )
        )
        .from(conversation)
        .leftJoin(dm).on(
            dm.conversation.id.eq(conversation.id)
            .and(
                dm.createdAt.eq(
                    JPAExpressions.select(subDm.createdAt.max())
                        .from(subDm)
                        .where(subDm.conversation.id.eq(conversation.id))
                )
            )
        )
        .where(
            conversation.user1.id.eq(userId).or(conversation.user2.id.eq(userId)),
            keywordLike(keywordLike, userId),
            cursorCondition(cursor, idAfter)
        )
        .orderBy(dm.createdAt.desc(), conversation.id.desc())
        .limit(limit + 1)
        .fetch();
    }

    private BooleanExpression keywordLike(String keywordLike, UUID userId) {
        if (keywordLike == null || keywordLike.isBlank()) {
            return null;
        }
        // 대화 상대방의 이름에 검색 키워드가 포함되는지 확인
        return new CaseBuilder()
            .when(conversation.user1.id.eq(userId)).then(conversation.user2.name)
            .otherwise(conversation.user1.name)
            .containsIgnoreCase(keywordLike);
    }

    private BooleanExpression cursorCondition(String cursor, UUID idAfter) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        Instant cursorTime = Instant.parse(cursor);
        if (idAfter != null) {
            return dm.createdAt.lt(cursorTime)
                .or(dm.createdAt.eq(cursorTime).and(conversation.id.lt(idAfter)));
        }
        return dm.createdAt.lt(cursorTime);
    }
}
