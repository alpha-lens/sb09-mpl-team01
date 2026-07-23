package com.codeit.mpl.domain.user.mail;

import com.codeit.mpl.domain.user.event.PasswordResetMailEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordResetMailSender {

    private final JavaMailSender mailSender;

    @Async("notificationExecutor")
    public void send(PasswordResetMailEvent event) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(event.getEmail());
            message.setSubject("[모두의 플리] 임시 비밀번호 안내");
            message.setText("임시 비밀번호: " + event.getTempPassword() + "\n3분 이내에 로그인 후 비밀번호를 변경해주세요.");
            mailSender.send(message);
        } catch (Exception e) {
            log.error("임시 비밀번호 메일 발송 실패. email={}", event.getEmail(), e);
        }
    }
}
