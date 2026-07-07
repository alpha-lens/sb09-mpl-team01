package com.codeit.mpl.domain.conversation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.dto.ConversationQueryDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageDto;
import com.codeit.mpl.domain.conversation.dto.DirectMessageSendRequest;
import com.codeit.mpl.domain.conversation.entity.Conversation;
import com.codeit.mpl.domain.conversation.entity.DirectMessage;
import com.codeit.mpl.domain.conversation.repository.ConversationRepository;
import com.codeit.mpl.domain.conversation.repository.DirectMessageRepository;
import com.codeit.mpl.domain.notification.event.NotificationEvent;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import com.codeit.mpl.infra.common.dto.Direction;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import com.codeit.mpl.infra.exception.conversation.ConversationNotFoundException;
import com.codeit.mpl.infra.exception.user.UserNotFoundException;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

import com.codeit.mpl.domain.notification.entity.NotificationType;
import com.codeit.mpl.domain.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

  @Mock
  private ConversationRepository conversationRepository;

  @Mock
  private DirectMessageRepository directMessageRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private NotificationRepository notificationRepository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @InjectMocks
  private ConversationService conversationService;

  @Test
  @DisplayName("대화방 단건 조회 - 성공")
  void getConversation_success() {
    // given
    UUID conversationId = UUID.randomUUID();
    Conversation conversation = mock(Conversation.class);
    given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

    // when
    Conversation result = conversationService.getConversation(conversationId);

    // then
    assertThat(result).isEqualTo(conversation);
    then(conversationRepository).should().findById(conversationId);
  }

  @Test
  @DisplayName("대화방 단건 조회 - 존재하지 않는 경우 예외")
  void getConversation_notFound() {
    // given
    UUID conversationId = UUID.randomUUID();
    given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> conversationService.getConversation(conversationId))
        .isInstanceOf(ConversationNotFoundException.class)
        .hasMessageContaining("존재하지 않는 대화방입니다.");
  }

  @Test
  @DisplayName("대화방 DTO 조회 - 성공")
  void getConversationDto_success() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();

    User currentUser = mock(User.class);
    given(currentUser.getId()).willReturn(currentUserId);

    User otherUser = mock(User.class);
    given(otherUser.getId()).willReturn(otherUserId);
    given(otherUser.getName()).willReturn("상대방");
    given(otherUser.getProfileImageUrl()).willReturn("http://image");

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);
    given(conversation.getUser1()).willReturn(currentUser);
    given(conversation.getUser2()).willReturn(otherUser);

    DirectMessage lastDm = mock(DirectMessage.class);
    given(lastDm.getId()).willReturn(UUID.randomUUID());
    given(lastDm.getConversation()).willReturn(conversation);
    given(lastDm.getSender()).willReturn(otherUser);
    given(lastDm.getReceiver()).willReturn(currentUser);
    given(lastDm.getCreatedAt()).willReturn(Instant.now());
    given(lastDm.getContent()).willReturn("마지막 메시지");

    given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
    given(directMessageRepository.findFirstByConversationIdOrderByCreatedAtDesc(conversationId)).willReturn(lastDm);
    given(directMessageRepository.countByConversationIdAndIsReadFalseAndReceiverId(conversationId, currentUserId)).willReturn(3L);

    // when
    ConversationDto result = conversationService.getConversationDto(conversationId, currentUserId);

    // then
    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(conversationId);
    assertThat(result.hasUnread()).isTrue();
    assertThat(result.lastMessage().content()).isEqualTo("마지막 메시지");
  }

  @Test
  @DisplayName("대화방 생성 - 기존 대화방 존재 시 기존 대화방 반환")
  void createConversation_existing() {
    // given
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    ConversationCreateRequest request = new ConversationCreateRequest(otherUserId);

    User user1 = mock(User.class);
    given(user1.getId()).willReturn(currentUserId);

    User user2 = mock(User.class);
    given(user2.getId()).willReturn(otherUserId);
    given(user2.getName()).willReturn("상대방");

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(UUID.randomUUID());
    given(conversation.getUser1()).willReturn(user1);
    given(conversation.getUser2()).willReturn(user2);

    given(userRepository.findById(currentUserId)).willReturn(Optional.of(user1));
    given(userRepository.findById(otherUserId)).willReturn(Optional.of(user2));
    given(conversationRepository.findBetweenUsers(currentUserId, otherUserId)).willReturn(Optional.of(conversation));

    // when
    ConversationDto result = conversationService.createConversation(currentUserId, request);

    // then
    assertThat(result).isNotNull();
    then(conversationRepository).should(never()).save(any());
  }

  @Test
  @DisplayName("대화방 생성 - 대화방이 존재하지 않는 경우 신규 대화방 생성 및 환영 메시지")
  void createConversation_new() {
    // given
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    ConversationCreateRequest request = new ConversationCreateRequest(otherUserId);

    User user1 = mock(User.class);
    given(user1.getId()).willReturn(currentUserId);

    User user2 = mock(User.class);
    given(user2.getId()).willReturn(otherUserId);
    given(user2.getName()).willReturn("상대방");

    given(userRepository.findById(currentUserId)).willReturn(Optional.of(user1));
    given(userRepository.findById(otherUserId)).willReturn(Optional.of(user2));
    given(conversationRepository.findBetweenUsers(currentUserId, otherUserId)).willReturn(Optional.empty());

    // when
    ConversationDto result = conversationService.createConversation(currentUserId, request);

    // then
    assertThat(result).isNotNull();
    then(conversationRepository).should().save(any(Conversation.class));
    then(directMessageRepository).should().save(any(DirectMessage.class));
  }

  @Test
  @DisplayName("대화방 목록 조회 - 성공")
  void getConversations_success() {
    // given
    UUID userId = UUID.randomUUID();
    CursorPageRequestDto request = new CursorPageRequestDto("cursor", UUID.randomUUID(), 10, Direction.DESCENDING, "createdAt");

    ConversationQueryDto queryDto = new ConversationQueryDto(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "상대방",
        "http://profile",
        UUID.randomUUID(),
        "마지막 내용",
        Instant.now(),
        2L
    );

    given(conversationRepository.findAllConversationsWithStats(eq(userId), any(), eq("cursor"), any(), eq(10)))
        .willReturn(List.of(queryDto));

    // when
    CursorPageResponseDto<ConversationDto> result = conversationService.getConversations(userId, "keyword", request);

    // then
    assertThat(result.data()).hasSize(1);
    assertThat(result.hasNext()).isFalse();
  }

  @Test
  @DisplayName("대화방 메시지 읽음 처리 - 성공 조건 부합 시 read() 호출")
  void readConversationMessages_success() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);

    User receiver = mock(User.class);
    given(receiver.getId()).willReturn(userId);

    DirectMessage dm = mock(DirectMessage.class);
    given(dm.getConversation()).willReturn(conversation);
    given(dm.getReceiver()).willReturn(receiver);
    given(dm.isRead()).willReturn(false);

    given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(dm));

    // when
    conversationService.readConversationMessages(conversationId, directMessageId, userId);

    // then
    then(dm).should().read();
    then(notificationRepository).should().deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(userId, NotificationType.DM, conversationId);
  }

  @Test
  @DisplayName("대화방 메시지 읽음 처리 - 조건 미부합 시 read() 호출 안 함")
  void readConversationMessages_noAction() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);

    User receiver = mock(User.class);
    given(receiver.getId()).willReturn(UUID.randomUUID()); // 다른 수신자

    DirectMessage dm = mock(DirectMessage.class);
    given(dm.getConversation()).willReturn(conversation);
    given(dm.getReceiver()).willReturn(receiver);

    given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(dm));

    // when
    conversationService.readConversationMessages(conversationId, directMessageId, userId);

    // then
    then(dm).should(never()).read();
  }

  @Test
  @DisplayName("메시지 목록 조회 - 성공")
  void getDirectMessages_success() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    CursorPageRequestDto request = new CursorPageRequestDto(null, UUID.randomUUID(), 10, Direction.DESCENDING, "createdAt");

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);

    User sender = mock(User.class);
    given(sender.getId()).willReturn(UUID.randomUUID());

    User receiver = mock(User.class);
    given(receiver.getId()).willReturn(userId);

    DirectMessage dm = mock(DirectMessage.class);
    given(dm.getId()).willReturn(UUID.randomUUID());
    given(dm.getConversation()).willReturn(conversation);
    given(dm.getSender()).willReturn(sender);
    given(dm.getReceiver()).willReturn(receiver);
    given(dm.isRead()).willReturn(false);

    given(directMessageRepository.findMessages(eq(conversationId), any(), any(Pageable.class)))
        .willReturn(List.of(dm));

    // when
    CursorPageResponseDto<DirectMessageDto> result = conversationService.getDirectMessages(conversationId, userId, request);

    // then
    assertThat(result.data()).hasSize(1);
    then(dm).should().read(); // 수신 메시지이므로 읽음 처리되어야 함
    then(notificationRepository).should().deleteByReceiverIdAndTypeAndTargetIdAndIsReadFalse(userId, NotificationType.DM, conversationId);
  }

  @Test
  @DisplayName("메시지 저장 및 알림 이벤트 발행 - 성공")
  void saveDirectMessage_success() {
    // given
    UUID conversationId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    DirectMessageSendRequest request = new DirectMessageSendRequest("안녕");

    User sender = mock(User.class);
    given(sender.getId()).willReturn(senderId);
    given(sender.getName()).willReturn("보낸이");

    User receiver = mock(User.class);
    given(receiver.getId()).willReturn(UUID.randomUUID());

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(conversationId);
    given(conversation.getUser1()).willReturn(sender);
    given(conversation.getUser2()).willReturn(receiver);

    given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
    given(userRepository.findById(senderId)).willReturn(Optional.of(sender));

    // when
    DirectMessageDto result = conversationService.saveDirectMessage(conversationId, senderId, request);

    // then
    assertThat(result).isNotNull();
    then(directMessageRepository).should().save(any(DirectMessage.class));
    then(eventPublisher).should().publishEvent(any(NotificationEvent.class));
  }

  @Test
  @DisplayName("상대 유저와의 대화방 조회 - 성공")
  void getWith_success() {
    // given
    UUID userId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    
    User user1 = mock(User.class);
    given(user1.getId()).willReturn(userId);
    User user2 = mock(User.class);
    given(user2.getId()).willReturn(targetUserId);

    Conversation conversation = mock(Conversation.class);
    given(conversation.getId()).willReturn(UUID.randomUUID());
    given(conversation.getUser1()).willReturn(user1);
    given(conversation.getUser2()).willReturn(user2);

    given(conversationRepository.findBetweenUsers(userId, targetUserId)).willReturn(Optional.of(conversation));

    // when
    ConversationDto result = conversationService.getWith(userId, targetUserId);

    // then
    assertThat(result).isNotNull();
  }

  @Test
  @DisplayName("상대 유저와의 대화방 조회 - 존재하지 않는 경우 null 반환")
  void getWith_notFound() {
    // given
    UUID userId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();

    given(conversationRepository.findBetweenUsers(userId, targetUserId)).willReturn(Optional.empty());

    // when
    ConversationDto result = conversationService.getWith(userId, targetUserId);

    // then
    assertThat(result).isNull();
  }

  @Test
  @DisplayName("대화방 생성 - 로그인 유저가 존재하지 않는 경우 예외")
  void createConversation_currentUserNotFound() {
    // given
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    ConversationCreateRequest request = new ConversationCreateRequest(otherUserId);

    given(userRepository.findById(currentUserId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> conversationService.createConversation(currentUserId, request))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("사용자를 찾을 수 없습니다.");
  }

  @Test
  @DisplayName("대화방 생성 - 상대방 유저가 존재하지 않는 경우 예외")
  void createConversation_otherUserNotFound() {
    // given
    UUID currentUserId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    ConversationCreateRequest request = new ConversationCreateRequest(otherUserId);

    User user1 = mock(User.class);
    given(userRepository.findById(currentUserId)).willReturn(Optional.of(user1));
    given(userRepository.findById(otherUserId)).willReturn(Optional.empty());

    // when & then
    assertThatThrownBy(() -> conversationService.createConversation(currentUserId, request))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining("사용자를 찾을 수 없습니다.");
  }
}
