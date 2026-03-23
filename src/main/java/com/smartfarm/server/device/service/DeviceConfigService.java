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

    @Value("${smartfarm.sensor.default-temp-threshold}")
    private double defaultTempThreshold;

    @Value("${smartfarm.sensor.default-mem-usage-threshold}")
    private double defaultMemUsageThreshold;

    @Cacheable(value = "deviceConfigView", key = "#deviceId")
    public DeviceConfigView getDeviceConfig(String deviceId) {
        log.info(">>> DB에서 {} 기기 설정값을 조회합니다. (이 로그가 보이면 캐시 미스 발생!)", deviceId);

        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    log.info(">>> 등록된 기기 설정이 없어 임시 기본 설정값을 반환합니다.");
                    return DeviceConfig.builder().deviceId(deviceId).build();
                });

        // null 임계값은 DeviceConfigView 생성 시 전역 yaml 기본값으로 채워짐 (엔티티는 변경하지 않음)
        return DeviceConfigView.from(config, defaultTempThreshold, defaultMemUsageThreshold);
    }

    public List<DeviceConfigResponseDto> getAllDeviceConfigs() {
        return deviceConfigRepository.findAll().stream()
                .map(e -> DeviceConfigResponseDto.from(e, defaultTempThreshold, defaultMemUsageThreshold))
                .toList();
    }

    /** REST API(DeviceConfigController)에서 호출하는 저장/수정 메서드 */
    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#request.deviceId")
    public DeviceConfigResponseDto saveOrUpdateDeviceConfig(DeviceConfigRequestDto request) {
        log.info(">>> 기기 설정 저장/수정 및 캐시 삭제: {}", request.getDeviceId());

        DeviceConfig config = deviceConfigRepository.findByDeviceId(request.getDeviceId())
                .orElse(DeviceConfig.builder()
                        .deviceId(request.getDeviceId())
                        .build());

        boolean isNew = config.getId() == null;

        if (config.getId() != null) {
            config.update(request.getTemperatureThresholdHigh(), request.getMemUsageThresholdHigh(),
                          request.getDiscordWebhookUrl());
        } else {
            // 신규 — 요청값이 있으면 설정, 없으면(null이면) 전역 기본값 상속(null 유지)
            if (request.getTemperatureThresholdHigh() != null) {
                config.setTemperatureThresholdHigh(request.getTemperatureThresholdHigh());
            }
            if (request.getMemUsageThresholdHigh() != null) {
                config.setMemUsageThresholdHigh(request.getMemUsageThresholdHigh());
            }
            config.update(request.getTemperatureThresholdHigh(), request.getMemUsageThresholdHigh(),
                          request.getDiscordWebhookUrl());
        }

        DeviceConfig saved = deviceConfigRepository.save(config);

        // 감사 로그: 기기 설정 변경 기록
        String changes = String.format("temperatureThreshold=%s, memUsageThreshold=%s, webhookUrl=%s",
                request.getTemperatureThresholdHigh(), request.getMemUsageThresholdHigh(),
                request.getDiscordWebhookUrl() != null ? "***" : "null");
        auditLogService.logDeviceConfigChange(request.getDeviceId(), getPrincipalName(), changes, getClientIp());

        return DeviceConfigResponseDto.from(saved, defaultTempThreshold, defaultMemUsageThreshold);
    }

    /** 내부(SensorService)에서 호출하는 기존 저장 메서드 - 하위 호환 유지 */
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

        // 관련 센서 이력 데이터 소프트 딜리트 (1주일 후 하드 딜리트 스케줄러가 처리)
        sensorHistoryRepository.softDeleteByDeviceId(deviceId, LocalDateTime.now());
        log.info(">>> {}의 센서 이력 데이터 소프트 딜리트 완료 (1주일 후 자동 삭제)", deviceId);
    }

    /**
     * 신규 PC 기기 자동 등록.
     * API 키 없이 deviceId 만으로 처음 한 번 등록하고 API 키를 발급받습니다.
     * 이미 등록된 deviceId면 DEVICE_ALREADY_EXISTS 예외를 발생시킵니다.
     *
     * <p>{@code @CacheEvict} 이유: 등록 전에 같은 deviceId로 보호 경로(예: /api/sensor/data)에
     * 접근 시도가 있었다면 {@link #getDeviceConfig(String)}이 apiKey=null 인 기본 설정을
     * 캐시에 저장했을 수 있습니다. 등록 완료 시 반드시 해당 캐시를 무효화해야 이후 인증이
     * 정상적으로 DB 값을 읽습니다.</p>
     */
    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#request.deviceId")
    public DeviceRegisterResponseDto registerDevice(DeviceRegisterRequestDto request) {
        String deviceId = request.getDeviceId();

        if (deviceConfigRepository.findByDeviceId(deviceId).isPresent()) {
            throw new CustomException(ErrorCode.DEVICE_ALREADY_EXISTS);
        }

        // 임계값 null → 전역 yaml 기본값 상속
        DeviceConfig config = DeviceConfig.builder()
                .deviceId(deviceId)
                .build();

        DeviceConfig saved = deviceConfigRepository.save(config); // @PrePersist 로 apiKey 자동 생성
        log.info(">>> 신규 기기 등록 완료: {} (apiKey 자동 발급)", deviceId);

        // 감사 로그: API 키 생성 기록
        auditLogService.logApiKeyGenerated(deviceId, getPrincipalName(), getClientIp());

        return DeviceRegisterResponseDto.from(saved, defaultTempThreshold, defaultMemUsageThreshold);
    }

    /**
     * API 키 재발급 — 기존 키를 새 UUID로 교체하고 캐시를 무효화합니다.
     */
    @Transactional
    @CacheEvict(value = "deviceConfigView", key = "#deviceId")
    public DeviceConfigResponseDto regenerateApiKey(String deviceId) {
        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        config.regenerateApiKey();
        DeviceConfig saved = deviceConfigRepository.save(config);
        log.info(">>> {} 기기 API 키 재발급 완료", deviceId);

        // 감사 로그: API 키 재발급 기록
        auditLogService.logApiKeyRenewal(deviceId, getPrincipalName(), getClientIp());

        return DeviceConfigResponseDto.from(saved, defaultTempThreshold, defaultMemUsageThreshold);
    }

    /**
     * 현재 인증된 사용자의 이름을 반환합니다.
     */
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

    /**
     * 현재 HTTP 요청의 클라이언트 IP를 추출합니다.
     */
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
