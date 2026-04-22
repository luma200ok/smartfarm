package com.smartfarm.server.device.service;

import com.smartfarm.server.common.util.CryptoService;
import com.smartfarm.server.device.dto.DeviceConfigRequestDto;
import com.smartfarm.server.device.dto.DeviceConfigResponseDto;
import com.smartfarm.server.device.dto.DeviceConfigView;
import com.smartfarm.server.device.dto.DeviceRegisterRequestDto;
import com.smartfarm.server.device.dto.DeviceRegisterResponseDto;
import com.smartfarm.server.device.entity.DeviceConfig;
import com.smartfarm.server.common.exception.CustomException;
import com.smartfarm.server.common.exception.ErrorCode;
import com.smartfarm.server.device.repository.DeviceConfigRepository;
import com.smartfarm.server.sensor.repository.SensorHistoryRepository;
import com.smartfarm.server.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigService {

    private final DeviceConfigRepository deviceConfigRepository;
    private final SensorHistoryRepository sensorHistoryRepository;
    private final AuditLogService auditLogService;
    private final CryptoService cryptoService;

    @Value("${smartfarm.sensor.default-temp-threshold-high}")
    private double defaultTempThresholdHigh;

    @Value("${smartfarm.sensor.default-temp-threshold-low}")
    private double defaultTempThresholdLow;

    @Value("${smartfarm.sensor.default-humidity-threshold-high}")
    private double defaultHumidityThresholdHigh;

    @Value("${smartfarm.sensor.default-humidity-threshold-low}")
    private double defaultHumidityThresholdLow;

    // -----------------------------------------------------------------------
    // 조회
    // -----------------------------------------------------------------------

    @Cacheable(value = "deviceConfigView", key = "#deviceId")
    public DeviceConfigView getDeviceConfig(String deviceId) {
        log.info(">>> DB에서 {} 기기 설정값을 조회합니다. (이 로그가 보이면 캐시 미스 발생!)", deviceId);

        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    log.info(">>> 등록된 기기 설정이 없어 임시 기본 설정값을 반환합니다.");
                    return DeviceConfig.builder().deviceId(deviceId).build();
                });

        return buildDecryptedView(config);
    }

    public List<DeviceConfigResponseDto> getAllDeviceConfigs() {
        return deviceConfigRepository.findAll().stream()
                .map(this::buildDecryptedResponseDto)
                .toList();
    }

    // -----------------------------------------------------------------------
    // 저장 / 수정
    // -----------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#request.deviceId")
    public DeviceConfigResponseDto saveOrUpdateDeviceConfig(DeviceConfigRequestDto request) {
        log.info(">>> 기기 설정 저장/수정 및 캐시 삭제: {}", request.getDeviceId());

        DeviceConfig config = deviceConfigRepository.findByDeviceId(request.getDeviceId())
                .orElse(DeviceConfig.builder()
                        .deviceId(request.getDeviceId())
                        .build());

        // Discord Webhook URL은 DB 저장 전 암호화
        String encryptedWebhookUrl = cryptoService.encrypt(request.getDiscordWebhookUrl());

        config.update(
                request.getTemperatureThresholdHigh(),
                request.getTemperatureThresholdLow(),
                request.getHumidityThresholdHigh(),
                request.getHumidityThresholdLow(),
                encryptedWebhookUrl
        );

        DeviceConfig saved = deviceConfigRepository.save(config);

        String changes = String.format(
                "tempHigh=%s, tempLow=%s, humidityHigh=%s, humidityLow=%s, webhookUrl=%s",
                request.getTemperatureThresholdHigh(), request.getTemperatureThresholdLow(),
                request.getHumidityThresholdHigh(), request.getHumidityThresholdLow(),
                request.getDiscordWebhookUrl() != null ? "***" : "null");
        auditLogService.logDeviceConfigChange(request.getDeviceId(), getPrincipalName(), changes, getClientIp());

        return buildDecryptedResponseDto(saved);
    }

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#deviceConfig.deviceId")
    public void saveOrUpdateDeviceConfig(DeviceConfig deviceConfig) {
        log.info(">>> 기기 설정 저장 및 캐시 삭제: {}", deviceConfig.getDeviceId());
        deviceConfigRepository.save(deviceConfig);
    }

    // -----------------------------------------------------------------------
    // 삭제
    // -----------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#deviceId")
    public void deleteDeviceConfig(String deviceId) {
        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        log.info(">>> 기기 설정 삭제 및 캐시 삭제: {}", deviceId);
        deviceConfigRepository.delete(config);

        sensorHistoryRepository.softDeleteByDeviceId(deviceId, LocalDateTime.now());
        log.info(">>> {}의 센서 이력 데이터 소프트 딜리트 완료 (1주일 후 자동 삭제)", deviceId);
    }

    // -----------------------------------------------------------------------
    // 기기 등록 / API 키 재발급
    // -----------------------------------------------------------------------

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#request.deviceId")
    public DeviceRegisterResponseDto registerDevice(DeviceRegisterRequestDto request) {
        String deviceId = request.getDeviceId();

        if (deviceConfigRepository.findByDeviceId(deviceId).isPresent()) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
        }

        // 서비스 레이어에서 UUID 생성 → 암호화 후 저장
        String plainApiKey     = UUID.randomUUID().toString();
        String encryptedApiKey = cryptoService.encrypt(plainApiKey);

        DeviceConfig config = DeviceConfig.builder()
                .deviceId(deviceId)
                .build();
        config.setApiKey(encryptedApiKey);

        DeviceConfig saved = deviceConfigRepository.save(config);
        log.info(">>> 신규 기기 등록 완료: {} (API 키 암호화 저장)", deviceId);

        auditLogService.logApiKeyGenerated(deviceId, getPrincipalName(), getClientIp());

        // 최초 발급 시에만 평문 API 키를 응답에 포함 (재조회 불가)
        return DeviceRegisterResponseDto.builder()
                .deviceId(saved.getDeviceId())
                .apiKey(plainApiKey)
                .temperatureThresholdHigh(defaultTempThresholdHigh)
                .temperatureThresholdLow(defaultTempThresholdLow)
                .humidityThresholdHigh(defaultHumidityThresholdHigh)
                .humidityThresholdLow(defaultHumidityThresholdLow)
                .message("기기 등록 완료. API 키를 .env 파일에 저장하세요. 재조회 불가 — 분실 시 대시보드에서 재발급하세요.")
                .build();
    }

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#deviceId")
    public DeviceConfigResponseDto regenerateApiKey(String deviceId) {
        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));

        // 서비스 레이어에서 새 UUID 생성 → 암호화 후 저장
        String plainApiKey = config.regenerateApiKey(); // 엔티티에서 plain UUID 반환
        config.setApiKey(cryptoService.encrypt(plainApiKey));

        DeviceConfig saved = deviceConfigRepository.save(config);
        log.info(">>> {} 기기 API 키 재발급 완료 (암호화 저장)", deviceId);

        auditLogService.logApiKeyRenewal(deviceId, getPrincipalName(), getClientIp());

        return buildDecryptedResponseDto(saved);
    }

    // -----------------------------------------------------------------------
    // 내부 헬퍼 — 복호화 후 DTO 조립
    // -----------------------------------------------------------------------

    /**
     * DB에서 읽은 엔티티(암호화된 필드 포함)를 복호화하여 캐시/응답용 View로 변환합니다.
     */
    private DeviceConfigView buildDecryptedView(DeviceConfig config) {
        return new DeviceConfigView(
                config.getDeviceId(),
                config.getTemperatureThresholdHigh() != null
                        ? config.getTemperatureThresholdHigh() : defaultTempThresholdHigh,
                config.getTemperatureThresholdLow() != null
                        ? config.getTemperatureThresholdLow() : defaultTempThresholdLow,
                config.getHumidityThresholdHigh() != null
                        ? config.getHumidityThresholdHigh() : defaultHumidityThresholdHigh,
                config.getHumidityThresholdLow() != null
                        ? config.getHumidityThresholdLow() : defaultHumidityThresholdLow,
                cryptoService.decrypt(config.getApiKey()),
                cryptoService.decrypt(config.getDiscordWebhookUrl())
        );
    }

    /**
     * DB에서 읽은 엔티티(암호화된 필드 포함)를 복호화하여 API 응답 DTO로 변환합니다.
     */
    private DeviceConfigResponseDto buildDecryptedResponseDto(DeviceConfig config) {
        return DeviceConfigResponseDto.builder()
                .id(config.getId())
                .deviceId(config.getDeviceId())
                .temperatureThresholdHigh(config.getTemperatureThresholdHigh())
                .temperatureThresholdLow(config.getTemperatureThresholdLow())
                .humidityThresholdHigh(config.getHumidityThresholdHigh())
                .humidityThresholdLow(config.getHumidityThresholdLow())
                .globalTempThresholdHigh(defaultTempThresholdHigh)
                .globalTempThresholdLow(defaultTempThresholdLow)
                .globalHumidityThresholdHigh(defaultHumidityThresholdHigh)
                .globalHumidityThresholdLow(defaultHumidityThresholdLow)
                .apiKey(cryptoService.decrypt(config.getApiKey()))
                .discordWebhookUrl(cryptoService.decrypt(config.getDiscordWebhookUrl()))
                .build();
    }

    // -----------------------------------------------------------------------
    // 보안 유틸
    // -----------------------------------------------------------------------

    private String getPrincipalName() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Failed to extract principal name", e);
        }
        return "SYSTEM";
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "UNKNOWN";
            }
            HttpServletRequest request = attributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("Failed to extract client IP", e);
            return "UNKNOWN";
        }
    }
}
