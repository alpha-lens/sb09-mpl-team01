package com.codeit.mpl.infra.common;

import com.codeit.mpl.domain.user.entity.User;
import com.codeit.mpl.domain.user.entity.UserRole;
import com.codeit.mpl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Value("${admin.name}")
    private String adminName;

    @Override
    public void run(ApplicationArguments args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            userRepository.save(User.builder()
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword))
                    .name(adminName)
                    .role(UserRole.ADMIN)
                    .build());
        }
    }
}
