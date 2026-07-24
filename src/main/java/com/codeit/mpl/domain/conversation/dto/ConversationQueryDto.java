package com.codeit.mpl.domain.conversation.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationQueryDto(
    UUID conversationId,
    UUID otherUserId,
    String otherUserName,
    String otherUserProfileImage,
    UUID lastMessageId,
    String lastMessageContent,
    Instant lastMessageCreatedAt,
    UUID senderId,
    String senderName,
    String senderProfileImage,
    UUID receiverId,
    String receiverName,
    String receiverProfileImage,
    long unreadCount
) {}