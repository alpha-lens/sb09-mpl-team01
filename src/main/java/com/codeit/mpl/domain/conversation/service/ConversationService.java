package com.codeit.mpl.domain.conversation.service;
import com.codeit.mpl.domain.conversation.dto.ConversationCreateRequest;
import com.codeit.mpl.domain.conversation.dto.ConversationDto;
import com.codeit.mpl.domain.conversation.entity.Conversation;
import com.codeit.mpl.domain.conversation.repository.ConversationRepository;
import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.repository.UserRepository;
import com.codeit.mpl.infra.common.dto.CursorPageRequestDto;
import com.codeit.mpl.infra.common.dto.CursorPageResponseDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {
  private final ConversationRepository conversationRepository;
  private final UserRepository userRepository;

  public Conversation getConversation(UUID conversationId) {
    return conversationRepository.findById(conversationId).orElseThrow();
  }

  /* TODO:
  * 1. Spring Filter에서 요청 사용자를 추출해야 하고
  * 2. Controller에서 해당 값을 Service로 전달해야 하고
  * 3. 두 사용자 모두 정상적인 사용자인지를 검증해야 함
  * 4. Dto로 만들어서 반환하는 처리 필요.
  * */
  public ConversationDto createConversation(ConversationCreateRequest request) {
    User user1 = null;
    User user2 = userRepository.findById(request.withUserId()).orElseThrow();
    Conversation conversation = Conversation.create(user1, user2);
    conversationRepository.save(conversation);
    return toDto(conversation);
  }

  public void readConversationMessages(UUID conversationId) {
    return;
  }

  public CursorPageResponseDto getDirectMessages(
      UUID conversationId, CursorPageRequestDto request
  ) {
    return null;
  }

  public ConversationDto getWith() {
    return null;
  }

  private ConversationDto toDto(Conversation conversation) {
    return new ConversationDto(
        conversation.getId(),
        null, // UserSummary를 넣어야 함.
        null, // 마지막 메시지 내역을 넣어야 함.
        false // 마지막 읽은 시간과 실제 메시지 시간을 비교하여 넣도록 수정해야 함
    );

  }
}
