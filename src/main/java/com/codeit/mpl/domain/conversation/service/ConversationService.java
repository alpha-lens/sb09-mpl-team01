package com.codeit.mpl.domain.conversation.service;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageSendRequest;
import com.codeit.mpl.domain.conversation.entity.Conversation;
import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import com.codeit.mpl.domain.conversation.repository.ConversationRepository;
import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;

    public Conversation getConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 대화방입니다."));
    }

    public ConversationDto getConversationDto(UUID conversationId, UUID userId) {
        Conversation conversation = getConversation(conversationId);
        return toDto(conversation, userId);
    }

    public ConversationDto createConversation(UUID currentUserId, ConversationCreateRequest request) {
        User user1 = userRepository.findById(currentUserId)
                .orElseThrow(() -> new IllegalArgumentException("로그인 유저가 유효하지 않습니다."));
        User user2 = userRepository.findById(request.withUserId())
                .orElseThrow(() -> new IllegalArgumentException("상대방 유저가 존재하지 않습니다."));

        Conversation conversation = conversationRepository.findBetweenUsers(currentUserId, request.withUserId())
                .orElseGet(() -> {
                    Conversation newConv = Conversation.create(user1, user2);
                    conversationRepository.save(newConv);
                    
                    // 최초 대화 생성 시, lastMessage @NotNull 제약을 만족시키기 위해 기본 메시지를 생성하여 저장합니다.
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

    public List<ConversationDto> getConversations(UUID userId) {
        List<Conversation> list = conversationRepository.findAllByUserId(userId);
        return list.stream()
                .map(c -> toDto(c, userId))
                .toList();
    }

    public void readConversationMessages(UUID conversationId, UUID userId) {
        // 해당 대화방에서 내가 수신한(receiver가 나인) 안 읽은 메시지들을 읽음 처리
        List<DirectMessage> list = directMessageRepository.findAll().stream()
                .filter(dm -> dm.getConversation().getId().equals(conversationId)
                        && dm.getReceiver().getId().equals(userId)
                        && !dm.isRead())
                .toList();
        list.forEach(DirectMessage::read);
    }

    public CursorPageResponseDto<DirectMessageDto> getDirectMessages(
            UUID conversationId, UUID userId, CursorPageRequestDto request
    ) {
        int limit = request.limit() != null ? request.limit() : 20;
        UUID idAfter = request.idAfter(); // ID 커서

        Pageable pageable = PageRequest.of(0, limit + 1);
        List<DirectMessage> list = directMessageRepository.findMessages(conversationId, idAfter, pageable);

        boolean hasNext = list.size() > limit;
        if (hasNext) {
            list = list.subList(0, limit);
        }

        List<DirectMessageDto> dtos = list.stream()
                .map(this::toDmDto)
                .toList();

        // 조회 후 내가 수신한 메시지들은 모두 읽음 처리합니다.
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

    public ConversationDto getWith(UUID userId, UUID targetUserId) {
        return conversationRepository.findBetweenUsers(userId, targetUserId)
                .map(c -> toDto(c, userId))
                .orElse(null);
    }

    @Transactional
    public DirectMessageDto saveDirectMessage(UUID conversationId, UUID senderId, DirectMessageSendRequest request) {
        Conversation conversation = getConversation(conversationId);
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("보낸 사람이 유효하지 않습니다."));
        
        // 수신자 식별 (대화 참여자 중 발신자가 아닌 쪽)
        User receiver = conversation.getUser1().getId().equals(senderId) ? conversation.getUser2() : conversation.getUser1();

        DirectMessage dm = DirectMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .receiver(receiver)
                .content(request.content())
                .isRead(false)
                .build();

        directMessageRepository.save(dm);
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
