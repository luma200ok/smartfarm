package com.smartfarm.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final DeviceApiKeyAuthFilter deviceApiKeyAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // PC 클라이언트 전용 엔드포인트는 DeviceApiKeyAuthFilter가 API 키를 검증합니다.
        // Spring Security는 permitAll()로 통과시키되, 필터에서 인증을 강제합니다.
        http
            .addFilterBefore(deviceApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/api/sensor/**").permitAll()
                // PC 클라이언트 전용 엔드포인트 — Spring Security는 허용, DeviceApiKeyAuthFilter가 API 키 검증
                .requestMatchers("/api/device-control/pending", "/api/device-control/ack").permitAll()
                .requestMatchers("/api/sse/device-command-stream").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/?error=true")
                .usernameParameter("username")
                .passwordParameter("password")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .csrf(csrf -> csrf
                // PC 클라이언트 전용 엔드포인트만 CSRF 면제 (인증이 없는 machine-to-machine 통신)
                // 브라우저에서 호출하는 /api/device-control/command, /api/device-config/** 등은 CSRF 보호 유지
                .ignoringRequestMatchers(
                        "/api/sensor/**",
                        "/api/device-control/pending",
                        "/api/device-control/ack",
                        "/api/sse/device-command-stream"
                )
            );

        return http.build();
    }
}
