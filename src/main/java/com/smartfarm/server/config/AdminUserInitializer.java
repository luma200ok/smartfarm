package com.smartfarm.server.config;

import com.smartfarm.server.entity.User;
import com.smartfarm.server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminUserInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("admin1234"))
                    .role("ROLE_ADMIN")
                    .build();
            userRepository.save(admin);
            log.info(">>> 초기 관리자 계정 생성 완료 (username: admin)");
        }

        if (userRepository.findByUsername("user").isEmpty()) {
            User user = User.builder()
                    .username("user")
                    .password(passwordEncoder.encode("user1234"))
                    .role("ROLE_USER")
                    .build();
            userRepository.save(user);
            log.info(">>> 초기 일반 사용자 계정 생성 완료 (username: user) — 조회 전용");
        }
    }
}
