package com.smartfarm.server.device.service;

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

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigService {

    private final DeviceConfigRepository deviceConfigRepository;
    private final SensorHistoryRepository sensorHistoryRepository;
    private final AuditLogService auditLogService;

    @Value("${smartfarm.sensor.default-temp-threshold-high}")
    private double defaultTempThresholdHigh;

    @Value("${smartfarm.sensor.default-temp-threshold-low}")
    private double defaultTempThresholdLow;

    @Value("${smartfarm.sensor.default-humidity-threshold-high}")
    private double defaultHumidityThresholdHigh;

    @Value("${smartfarm.sensor.default-humidity-threshold-low}")
    private double defaultHumidityThresholdLow;

    @Cacheable(value = "deviceConfigView", key = "#deviceId")
    public DeviceConfigView getDeviceConfig(String deviceId) {
        log.info(">>> DB에서 {} 기기 설정값을 조회합니다. (이 로그가 보이면 캐시 미스 발생!)", deviceId);

        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    log.info(">>> 등록된 기기 설정이 없어 임시 기본 설정값을 반환합니다.");
                    return DeviceConfig.builder().deviceId(deviceId).build();
                });

        return DeviceConfigView.from(config,
                defaultTempThresholdHigh, defaultTempThresholdLow,
                defaultHumidityThresholdHigh, defaultHumidityThresholdLow);
    }

    public List<DeviceConfigResponseDto> getAllDeviceConfigs() {
        return deviceConfigRepository.findAll().stream()
                .map(e -> DeviceConfigResponseDto.from(e,
                        defaultTempThresholdHigh, defaultTempThresholdLow,
                        defaultHumidityThresholdHigh, defaultHumidityThresholdLow))
                .toList();
    }

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#request.deviceId")
    public DeviceConfigResponseDto saveOrUpdateDeviceConfig(DeviceConfigRequestDto request) {
        log.info(">>> 기기 설정 저장/수정 및 캐시 삭제: {}", request.getDeviceId());

        DeviceConfig config = deviceConfigRepository.findByDeviceId(request.getDeviceId())
                .orElse(DeviceConfig.builder()
                        .deviceId(request.getDeviceId())
                        .build());

        config.update(
                request.getTemperatureThresholdHigh(),
                request.getTemperatureThresholdLow(),
                request.getHumidityThresholdHigh(),
                request.getHumidityThresholdLow(),
                request.getDiscordWebhookUrl()
        );

        DeviceConfig saved = deviceConfigRepository.save(config);

        String changes = String.format(
                "tempHigh=%s, tempLow=%s, humidityHigh=%s, humidityLow=%s, webhookUrl=%s",
                request.getTemperatureThresholdHigh(), request.getTemperatureThresholdLow(),
                request.getHumidityThresholdHigh(), request.getHumidityThresholdLow(),
                request.getDiscordWebhookUrl() != null ? "***" : "null");
        auditLogService.logDeviceConfigChange(request.getDeviceId(), getPrincipalName(), changes, getClientIp());

        return DeviceConfigResponseDto.from(saved,
                defaultTempThresholdHigh, defaultTempThresholdLow,
                defaultHumidityThresholdHigh, defaultHumidityThresholdLow);
    }

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#deviceConfig.deviceId")
    public void saveOrUpdateDeviceConfig(DeviceConfig deviceConfig) {
        log.info(">>> 기기 설정 저장 및 캐시 삭제: {}", deviceConfig.getDeviceId());
        deviceConfigRepository.save(deviceConfig);
    }

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

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#request.deviceId")
    public DeviceRegisterResponseDto registerDevice(DeviceRegisterRequestDto request) {
        String deviceId = request.getDeviceId();

        if (deviceConfigRepository.findByDeviceId(deviceId).isPresent()) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
        }

        DeviceConfig config = DeviceConfig.builder()
                .deviceId(deviceId)
                .build();

        DeviceConfig saved = deviceConfigRepository.save(config);
        log.info(">>> 신규 기기 등록 완료: {} (apiKey 자동 발급)", deviceId);

        auditLogService.logApiKeyGenerated(deviceId, getPrincipalName(), getClientIp());

        return DeviceRegisterResponseDto.from(saved,
                defaultTempThresholdHigh, defaultTempThresholdLow,
                defaultHumidityThresholdHigh, defaultHumidityThresholdLow);
    }

    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#deviceId")
    public DeviceConfigResponseDto regenerateApiKey(String deviceId) {
        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        config.regenerateApiKey();
        DeviceConfig saved = deviceConfigRepository.save(config);
        log.info(">>> {} 기기 API 키 재발급 완료", deviceId);

        auditLogService.logApiKeyRenewal(deviceId, getPrincipalName(), getClientIp());

        return DeviceConfigResponseDto.from(saved,
                defaultTempThresholdHigh, defaultTempThresholdLow,
                defaultHumidityThresholdHigh, defaultHumidityThresholdLow);
    }

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
