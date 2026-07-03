package com.codeit.mpl.domain.conversation.service;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.ConversationQueryDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageSendRequest;
import com.codeit.mpl.domain.conversation.entity.Conversation;
import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import com.codeit.mpl.domain.conversation.repository.ConversationRepository;
import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.notification.entity.NotificationLevel;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.codeit.mpl.infra.exception.conversation.ConversationNotFoundException;
import com.codeit.mpl.infra.exception.user.UserNotFoundException;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
            .orElseThrow(ConversationNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public ConversationDto getConversationDto(UUID conversationId, UUID userId) {
        // 단건 조회 시에도 N+1을 방어하기 위해 queryRepository나 단건 통계 쿼리를 쓰는 것이 좋으나,
        // 단건 조회가 잦지 않다면 기존 toDto를 유지하되 아래 최적화된 메서드로 대체 가능합니다.
        Conversation conversation = getConversation(conversationId);
        return toDto(conversation, userId);
    }

    public ConversationDto createConversation(UUID currentUserId, ConversationCreateRequest request) {
        User user1 = userRepository.findById(currentUserId)
            .orElseThrow(UserNotFoundException::new);
        User user2 = userRepository.findById(request.withUserId())
            .orElseThrow(UserNotFoundException::new);

        Conversation conversation = conversationRepository.findBetweenUsers(currentUserId, request.withUserId())
            .orElseGet(() -> {
                Conversation newConv = Conversation.create(user1, user2);
                conversationRepository.save(newConv);

                DirectMessage welcomeMessage = DirectMessage.builder()
                    .conversation(newConv)
                    .sender(user1)
                    .receiver(user2)
                    .content("대화가 시작되었습니다.")
                    .isRead(true)
                    .build();
                directMessageRepository.save(welcomeMessage);
                return newConv;
            });

        return toDto(conversation, currentUserId);
    }

    @Transactional(readOnly = true)
    public CursorPageResponseDto<ConversationDto> getConversations(UUID userId, String keywordLike, CursorPageRequestDto request) {
        int limit = request.limit() != null ? request.limit() : 20;
        String cursor = request.cursor();
        UUID idAfter = request.idAfter();

        List<ConversationQueryDto> flatDtos = conversationRepository.findAllConversationsWithStats(
            userId, keywordLike, cursor, idAfter, limit
        );

        boolean hasNext = flatDtos.size() > limit;
        if (hasNext) {
            flatDtos = flatDtos.subList(0, limit);
        }

        List<ConversationDto> dtos = flatDtos.stream()
            .map(flat -> {
                UserSummary with = new UserSummary(
                    flat.otherUserId(),
                    flat.otherUserName(),
                    flat.otherUserProfileImage()
                );

                DirectMessageDto lastMessage = null;
                if (flat.lastMessageId() != null) {
                    lastMessage = new DirectMessageDto(
                        flat.lastMessageId(),
                        flat.conversationId(),
                        flat.lastMessageCreatedAt(),
                        null,
                        null,
                        flat.lastMessageContent()
                    );
                }

                return new ConversationDto(
                    flat.conversationId(),
                    with,
                    lastMessage,
                    flat.unreadCount() > 0
                );
            })
            .toList();

        String nextCursor = null;
        String nextIdAfter = null;
        if (!flatDtos.isEmpty()) {
            ConversationQueryDto last = flatDtos.get(flatDtos.size() - 1);
            if (last.lastMessageCreatedAt() != null) {
                nextCursor = last.lastMessageCreatedAt().toString();
            }
            nextIdAfter = last.conversationId().toString();
        }

        return new CursorPageResponseDto<>(
            dtos,
            nextCursor,
            nextIdAfter,
            hasNext,
            (long) dtos.size(), // totalCount (가볍게 목록 크기로 대체하거나 캐싱 적용)
            "createdAt",
            Direction.DESCENDING
        );
    }

    public void readConversationMessages(UUID conversationId, UUID directMessageId, UUID userId) {
        // [수정] 자신이 보낸 메시지이거나 이미 읽은 메시지인 경우 예외를 던지는 대신 조용히 처리하여 500 에러 방지
        directMessageRepository.findById(directMessageId)
            .ifPresent(dm -> {
                if (dm.getConversation().getId().equals(conversationId)
                        && dm.getReceiver().getId().equals(userId)
                        && !dm.isRead()) {
                    dm.read();
                }
            });
    }

    public CursorPageResponseDto<DirectMessageDto> getDirectMessages(
        UUID conversationId, UUID userId, CursorPageRequestDto request
    ) {
        int limit = request.limit() != null ? request.limit() : 20;
        UUID idAfter = request.idAfter();

        Pageable pageable = PageRequest.of(0, limit + 1);
        List<DirectMessage> list = directMessageRepository.findMessages(conversationId, idAfter, pageable);

        boolean hasNext = list.size() > limit;
        if (hasNext) {
            list = list.subList(0, limit);
        }

        List<DirectMessageDto> dtos = list.stream()
            .map(this::toDmDto)
            .toList();

        // [수정] 벌크성 읽음 처리를 위한 벌크 업데이트 메서드 호출 권장
        // 혹은 현재 트랜잭션 범위 안이므로 유지하되, 대상 건수가 많다면 JPQL UPDATE 문을 별도로 찌르는 것이 유리합니다.
        list.stream()
            .filter(dm -> dm.getReceiver().getId().equals(userId) && !dm.isRead())
            .forEach(DirectMessage::read);

        String nextCursor = null;
        String nextIdAfter = null;
        if (!dtos.isEmpty()) {
            DirectMessageDto last = dtos.get(dtos.size() - 1);
            nextCursor = last.id().toString();
            nextIdAfter = last.id().toString();
        }

        return new CursorPageResponseDto<>(
            dtos,
            nextCursor,
            nextIdAfter,
            hasNext,
            dtos.size(),
            "createdAt",
            Direction.DESCENDING
        );
    }

    @Transactional(readOnly = true)
    public ConversationDto getWith(UUID userId, UUID targetUserId) {
        return conversationRepository.findBetweenUsers(userId, targetUserId)
            .map(c -> toDto(c, userId))
            .orElse(null);
    }

    public DirectMessageDto saveDirectMessage(UUID conversationId, UUID senderId, DirectMessageSendRequest request) {
        Conversation conversation = getConversation(conversationId);
        User sender = userRepository.findById(senderId)
            .orElseThrow(UserNotFoundException::new);

        User receiver = conversation.getUser1().getId().equals(senderId) ? conversation.getUser2() : conversation.getUser1();

        DirectMessage dm = DirectMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .receiver(receiver)
            .content(request.content())
            .isRead(false)
            .build();

        directMessageRepository.save(dm);

        // DM 수신 시 실시간 알림을 위한 이벤트 발행
        eventPublisher.publishEvent(new NotificationEvent(
            receiver,
            sender,
            NotificationLevel.INFO,
            sender.getName() + "님으로부터 메시지가 도착했습니다.",
            request.content()
        ));

        return toDmDto(dm);
    }

    private ConversationDto toDto(Conversation conversation, UUID currentUserId) {
        User otherUser = conversation.getUser1().getId().equals(currentUserId) ? conversation.getUser2() : conversation.getUser1();
        UserSummary withSummary = new UserSummary(otherUser.getId(), otherUser.getName(), otherUser.getProfileImageUrl());

        DirectMessage lastDm = directMessageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversation.getId());
        DirectMessageDto lastMessageDto = lastDm != null ? toDmDto(lastDm) : new DirectMessageDto(
            UUID.randomUUID(),
            conversation.getId(),
            Instant.now(),
            withSummary,
            withSummary,
            "대화 기록이 없습니다."
        );

        long unreadCount = directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(conversation.getId(), currentUserId);
        boolean hasUnread = unreadCount > 0;

        return new ConversationDto(
            conversation.getId(),
            withSummary,
            lastMessageDto,
            hasUnread
        );
    }

    private DirectMessageDto toDmDto(DirectMessage dm) {
        UserSummary sender = new UserSummary(dm.getSender().getId(), dm.getSender().getName(), dm.getSender().getProfileImageUrl());
        UserSummary receiver = new UserSummary(dm.getReceiver().getId(), dm.getReceiver().getName(), dm.getReceiver().getProfileImageUrl());
        return new DirectMessageDto(
            dm.getId(),
            dm.getConversation().getId(),
            dm.getCreatedAt(),
            sender,
            receiver,
            dm.getContent()

        );
    }
}