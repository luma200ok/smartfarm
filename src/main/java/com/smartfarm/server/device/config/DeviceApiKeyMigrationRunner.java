package com.smartfarm.server.device.config;

import com.smartfarm.server.device.entity.DeviceConfig;
import com.smartfarm.server.device.repository.DeviceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 기존 DeviceConfig 행 중 apiKey가 NULL인 경우 UUID를 자동 발급합니다.
 * (apiKey 컬럼이 추가되기 전에 등록된 기기 대상 1회성 마이그레이션)
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DeviceApiKeyMigrationRunner implements ApplicationRunner {

    private final DeviceConfigRepository deviceConfigRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DeviceConfig> configs = deviceConfigRepository.findAll();
        long migrated = configs.stream()
                .filter(c -> c.getApiKey() == null)
                .peek(c -> {
                    c.generateApiKeyIfAbsent();
                    deviceConfigRepository.save(c);
                    log.info(">>> [API Key Migration] {} 기기 API 키 자동 발급 완료", c.getDeviceId());
                })
                .count();

        if (migrated > 0) {
            log.info(">>> [API Key Migration] 총 {}개 기기 API 키 마이그레이션 완료", migrated);
        }
    }
}
