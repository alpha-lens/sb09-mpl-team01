package com.codeit.mpl.domain.user.mail;

import com.codeit.mpl.domain.user.event.PasswordResetMailEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class PasswordResetMailSenderTest {

  @Mock
  private JavaMailSender mailSender;

  private PasswordResetMailSender passwordResetMailSender;

  @BeforeEach
  void setUp() {
    passwordResetMailSender = new PasswordResetMailSender(mailSender);
  }

  @Test
  @DisplayName("임시 비밀번호 메일 발송 성공 - 수신자/제목/본문을 채워서 전송한다")
  void send_success() {
    PasswordResetMailEvent event = new PasswordResetMailEvent("test@test.com", "TmpPass1");

    passwordResetMailSender.send(event);

    ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    then(mailSender).should().send(captor.capture());

    SimpleMailMessage sentMessage = captor.getValue();
    org.assertj.core.api.Assertions.assertThat(sentMessage.getTo()).containsExactly("test@test.com");
    org.assertj.core.api.Assertions.assertThat(sentMessage.getSubject()).contains("모두의 플리");
    org.assertj.core.api.Assertions.assertThat(sentMessage.getText()).contains("TmpPass1");
  }

  @Test
  @DisplayName("메일 발송 실패 - 예외를 잡아서 삼키고 호출자에게 전파하지 않는다")
  void send_failure_doesNotPropagateException() {
    PasswordResetMailEvent event = new PasswordResetMailEvent("test@test.com", "TmpPass1");
    willThrow(new MailSendException("smtp down")).given(mailSender).send(any(SimpleMailMessage.class));

    org.assertj.core.api.Assertions.assertThatCode(() -> passwordResetMailSender.send(event))
        .doesNotThrowAnyException();
  }
}
