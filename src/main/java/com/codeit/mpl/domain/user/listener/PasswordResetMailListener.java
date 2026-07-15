package com.codeit.mpl.domain.user.listener;

import com.codeit.mpl.domain.user.event.PasswordResetMailEvent;
import com.codeit.mpl.domain.user.mail.PasswordResetMailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class PasswordResetMailListener {

    private final PasswordResetMailSender passwordResetMailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PasswordResetMailEvent event) {
        passwordResetMailSender.send(event);
    }
}
