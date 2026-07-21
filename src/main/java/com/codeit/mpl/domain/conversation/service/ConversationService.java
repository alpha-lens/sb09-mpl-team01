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
import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;
import com.codeit.mpl.domain.user.dto.UserSummary;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import com.codeit.mpl.infra.storage.BinaryContentStorage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ConversationService {
    private final ConversationRepository conversationRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ActiveConversationManager activeConversationManager;
    private final BinaryContentStorage binaryContentStorage;

    @Transactional(readOnly = true)
    public Conversation getConversation(UUID conversationId) {
        log.debug("[ConversationService] DB에서 채팅방 단건 조회 요청 - conversationId: {}", conversationId);
        return conversationRepository.findById(conversationId)
            .orElseThrow(() -> {
                log.warn("[ConversationService] 채팅방 조회 실패 - 존재하지 않는 conversationId: {}", conversationId);
                return new ConversationNotFoundException();
            });
    }

    @Transactional(readOnly = true)
    public ConversationDto getConversationDto(UUID conversationId, UUID userId) {
        log.debug("[ConversationService] 채팅방 DTO 조회 요청 - conversationId: {}, userId: {}", conversationId, userId);
        Conversation conversation = getConversation(conversationId);
        return toDto(conversation, userId);
    }

    public ConversationDto createConversation(UUID currentUserId, ConversationCreateRequest request) {
        log.info("[ConversationService] 채팅방 생성 또는 조회 시작 - currentUserId: {}, targetUserId: {}", currentUserId, request.withUserId());
        User user1 = userRepository.findById(currentUserId)
            .orElseThrow(() -> {
                log.warn("[ConversationService] 채팅방 생성 실패 - 존재하지 않는 currentUserId: {}", currentUserId);
                return new UserNotFoundException();
            });
        User user2 = userRepository.findById(request.withUserId())
            .orElseThrow(() -> {
                log.warn("[ConversationService] 채팅방 생성 실패 - 존재하지 않는 targetUserId: {}", request.withUserId());
                return new UserNotFoundException();
            });

        Conversation conversation = conversationRepository.findBetweenUsers(currentUserId, request.withUserId())
            .map(conv -> {
                log.info("[ConversationService] 기존 채팅방 존재함 - conversationId: {}", conv.getId());
                return conv;
            })
            .orElseGet(() -> {
                log.info("[ConversationService] 기존 채팅방 없음. 새 채팅방 생성 진행 - user1: {}, user2: {}", currentUserId, request.withUserId());
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
                log.info("[ConversationService] 새 채팅방 및 웰컴 메시지 저장 완료 - conversationId: {}", newConv.getId());
                return newConv;
            });

        return toDto(conversation, currentUserId);
    }

    @Transactional(readOnly = true)
    public CursorPageResponseDto<ConversationDto> getConversations(UUID userId, String keywordLike, CursorPageRequestDto request) {
        int limit = request.limit() != null ? request.limit() : 20;
        String cursor = request.cursor();
        UUID idAfter = request.idAfter();

        log.debug("[ConversationService] 채팅방 목록 쿼리 실행 시작 - userId: {}, keywordLike: {}, limit: {}, cursor: {}", userId, keywordLike, limit, cursor);

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
                    resolveProfileImageUrl(flat.otherUserProfileImage())
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

        log.debug("[ConversationService] 채팅방 목록 쿼리 실행 완료 - userId: {}, 반환 개수: {}, hasNext: {}", userId, dtos.size(), hasNext);

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
        log.info("[ConversationService] 메시지 읽음 처리 요청 - conversationId: {}, directMessageId: {}, userId: {}", conversationId, directMessageId, userId);
        directMessageRepository.findById(directMessageId)
            .ifPresentOrElse(dm -> {
                if (dm.getConversation().getId().equals(conversationId)
                        && dm.getReceiver().getId().equals(userId)
                        && !dm.isRead()) {
                    dm.read();
                    log.info("[ConversationService] 메시지 읽음 처리 완료 - directMessageId: {}", directMessageId);
                    // 대화 메시지 읽음 시 해당 대화방의 모든 메시지가 읽음 상태인 경우에만 안 읽은 DM 알림 삭제
                    if (directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(conversationId, userId) == 0) {
                        notificationRepository.deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(userId, NotificationType.DM, conversationId);
                    }
                } else {
                    log.debug("[ConversationService] 메시지 읽음 처리 스킵 (자신의 메시지이거나 이미 읽음) - directMessageId: {}", directMessageId);
                }
            }, () -> log.warn("[ConversationService] 메시지 읽음 처리 실패 - 존재하지 않는 directMessageId: {}", directMessageId));
    }

    public CursorPageResponseDto<DirectMessageDto> getDirectMessages(
        UUID conversationId, UUID userId, CursorPageRequestDto request
    ) {
        int limit = request.limit() != null ? request.limit() : 20;
        UUID idAfter = request.idAfter();
        log.debug("[ConversationService] 메시지 목록 조회 시작 - conversationId: {}, userId: {}, limit: {}, idAfter: {}", conversationId, userId, limit, idAfter);

        Pageable pageable = PageRequest.of(0, limit + 1);
        List<DirectMessage> list = directMessageRepository.findMessages(conversationId, idAfter, pageable);

        boolean hasNext = list.size() > limit;
        if (hasNext) {
            list = list.subList(0, limit);
        }

        List<DirectMessageDto> dtos = list.stream()
            .map(this::toDmDto)
            .toList();

        long readCount = list.stream()
            .filter(dm -> dm.getReceiver().getId().equals(userId) && !dm.isRead())
            .peek(DirectMessage::read)
            .count();
        if (readCount > 0) {
            log.info("[ConversationService] 수신 메시지 읽음 처리 완료 - conversationId: {}, userId: {}, 읽음 처리된 개수: {}", conversationId, userId, readCount);
            // 대화방 진입으로 인한 메시지 읽음 시 해당 대화방의 모든 메시지가 읽음 상태인 경우에만 안 읽은 DM 알림 삭제
            if (directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(conversationId, userId) == 0) {
                notificationRepository.deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(userId, NotificationType.DM, conversationId);
            }
        }

        String nextCursor = null;
        String nextIdAfter = null;
        if (!dtos.isEmpty()) {
            DirectMessageDto last = dtos.get(dtos.size() - 1);
            nextCursor = last.id().toString();
            nextIdAfter = last.id().toString();
        }

        log.debug("[ConversationService] 메시지 목록 조회 완료 - conversationId: {}, 반환 메시지 개수: {}, hasNext: {}", conversationId, dtos.size(), hasNext);

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
        log.debug("[ConversationService] 상대방과의 채팅방 조회 - userId: {}, targetUserId: {}", userId, targetUserId);
        return conversationRepository.findBetweenUsers(userId, targetUserId)
            .map(c -> {
                log.debug("[ConversationService] 상대방과의 채팅방 존재함 - conversationId: {}", c.getId());
                return toDto(c, userId);
            })
            .orElseGet(() -> {
                log.debug("[ConversationService] 상대방과의 채팅방 존재하지 않음 - userId: {}, targetUserId: {}", userId, targetUserId);
                return null;
            });
    }

    public DirectMessageDto saveDirectMessage(UUID conversationId, UUID senderId, DirectMessageSendRequest request) {
        log.info("[ConversationService] 메시지 전송 시작 - conversationId: {}, senderId: {}", conversationId, senderId);
        Conversation conversation = getConversation(conversationId);
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> {
                log.warn("[ConversationService] 메시지 전송 실패 - 존재하지 않는 senderId: {}", senderId);
                return new UserNotFoundException();
            });

        User receiver = conversation.getUser1().getId().equals(senderId) ? conversation.getUser2() : conversation.getUser1();

        boolean isReceiverActive = activeConversationManager.isUserActiveInConversation(receiver.getId(), conversation.getId());

        DirectMessage dm = DirectMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .receiver(receiver)
            .content(request.content())
            .isRead(isReceiverActive)
            .build();

        directMessageRepository.save(dm);
        log.info("[ConversationService] 메시지 저장 완료 - directMessageId: {}", dm.getId());

        // 수신자가 대화방을 보고 있지 않을 때만 알림 이벤트 발행
        if (!isReceiverActive) {
            log.debug("[ConversationService] 알림 이벤트 발행 - receiverId: {}, title: {}", receiver.getId(), sender.getName() + "님으로부터 메시지가 도착했습니다.");
            eventPublisher.publishEvent(new NotificationEvent(
                receiver,
                sender,
                NotificationLevel.INFO,
                sender.getName() + "님으로부터 메시지가 도착했습니다.",
                request.content(),
                NotificationType.DM,
                conversation.getId()
            ));
        } else {
            log.debug("[ConversationService] 수신자가 대화방에 진입해 있으므로 알림 발행 생략 - receiverId: {}, conversationId: {}", receiver.getId(), conversationId);
        }

        return toDmDto(dm);
    }

    private ConversationDto toDto(Conversation conversation, UUID currentUserId) {
        User otherUser = conversation.getUser1().getId().equals(currentUserId) ? conversation.getUser2() : conversation.getUser1();
        UserSummary withSummary = new UserSummary(otherUser.getId(), otherUser.getName(), resolveProfileImageUrl(otherUser));

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

    // User.profileImageUrl 컬럼엔 S3 key가 저장되므로, 응답 시점마다 presigned URL로 변환해서 내려준다.
    private String resolveProfileImageUrl(User user) {
        return resolveProfileImageUrl(user.getProfileImageUrl());
    }

    // getConversations()는 엔티티가 아니라 QueryDSL 플랫 프로젝션(ConversationQueryDto)에서
    // key를 직접 받아오므로, User가 아닌 key(String) 그대로 받는 오버로드가 따로 필요하다.
    private String resolveProfileImageUrl(String profileImageKey) {
        return profileImageKey != null
            ? binaryContentStorage.getUrl(profileImageKey)
            : null;
    }

    private DirectMessageDto toDmDto(DirectMessage dm) {
        UserSummary sender = new UserSummary(dm.getSender().getId(), dm.getSender().getName(), resolveProfileImageUrl(dm.getSender()));
        UserSummary receiver = new UserSummary(dm.getReceiver().getId(), dm.getReceiver().getName(), resolveProfileImageUrl(dm.getReceiver()));
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