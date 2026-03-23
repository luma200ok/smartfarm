package com.smartfarm.server.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.smartfarm.server.device.filter.DeviceRegisterRateLimiter;

import java.time.Duration;

/**
 * /api/device/register 엔드포인트 남용 방지용 인메모리 Rate Limiter 설정
 * IP당 분당 최대 5회 허용
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public DeviceRegisterRateLimiter deviceRegisterRateLimiter() {
        return new DeviceRegisterRateLimiter(5, Duration.ofMinutes(1));
    }
}
