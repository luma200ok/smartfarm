package com.smartfarm.server.device.config;

import com.smartfarm.server.common.util.CryptoService;
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
 * DB에 평문으로 저장된 apiKey·discordWebhookUrl을 AES-256-GCM으로 암호화하는 1회성 마이그레이션 러너.
 *
 * <p>{@code ENC:} 접두사가 없는 값을 암호화 대상으로 식별합니다.
 * 이미 암호화된 값은 건너뜁니다.</p>
 *
 * <p>실행 순서: {@link DeviceApiKeyMigrationRunner}(@Order 2) 이후 동작하여
 * null → UUID 발급이 먼저 완료된 뒤 암호화를 수행합니다.</p>
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class EncryptionMigrationRunner implements ApplicationRunner {

    private final DeviceConfigRepository deviceConfigRepository;
    private final CryptoService cryptoService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<DeviceConfig> configs = deviceConfigRepository.findAll();
        long migrated = 0;

        for (DeviceConfig config : configs) {
            boolean changed = false;

            if (needsEncryption(config.getApiKey())) {
                config.setApiKey(cryptoService.encrypt(config.getApiKey()));
                changed = true;
                log.info(">>> [Encryption Migration] {} — API 키 암호화 완료", config.getDeviceId());
            }

            if (needsEncryption(config.getDiscordWebhookUrl())) {
                config.setDiscordWebhookUrl(cryptoService.encrypt(config.getDiscordWebhookUrl()));
                changed = true;
                log.info(">>> [Encryption Migration] {} — Discord Webhook URL 암호화 완료", config.getDeviceId());
            }

            if (changed) {
                deviceConfigRepository.save(config);
                migrated++;
            }
        }

        if (migrated > 0) {
            log.info(">>> [Encryption Migration] 총 {}개 기기 데이터 암호화 완료", migrated);
        } else {
            log.info(">>> [Encryption Migration] 암호화 마이그레이션 대상 없음");
        }
    }

    /** null이 아니고 아직 암호화되지 않은 값인지 확인 */
    private boolean needsEncryption(String value) {
        return value != null && !value.startsWith(CryptoService.ENC_PREFIX);
    }
}
