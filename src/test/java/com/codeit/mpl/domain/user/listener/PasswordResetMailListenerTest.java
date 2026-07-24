package com.codeit.mpl.domain.user.listener;

import com.codeit.mpl.domain.user.event.PasswordResetMailEvent;
import com.codeit.mpl.domain.user.mail.PasswordResetMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailListenerTest {

  @Mock
  private PasswordResetMailSender passwordResetMailSender;

  private PasswordResetMailListener passwordResetMailListener;

  @BeforeEach
  void setUp() {
    passwordResetMailListener = new PasswordResetMailListener(passwordResetMailSender);
  }

  @Test
  @DisplayName("커밋 후 이벤트를 받으면 그대로 메일 발송기에 위임한다")
  void handle_delegatesToMailSender() {
    PasswordResetMailEvent event = new PasswordResetMailEvent("test@test.com", "TmpPass1");

    passwordResetMailListener.handle(event);

    then(passwordResetMailSender).should().send(event);
  }
}
