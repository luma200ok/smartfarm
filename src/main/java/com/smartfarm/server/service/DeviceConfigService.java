package com.smartfarm.server.service;

import com.smartfarm.server.dto.DeviceConfigRequestDto;
import com.smartfarm.server.dto.DeviceConfigResponseDto;
import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.repository.DeviceConfigRepository;
import com.smartfarm.server.repository.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigService {

    private final DeviceConfigRepository deviceConfigRepository;
    private final SensorHistoryRepository sensorHistoryRepository;

    @Value("${smartfarm.sensor.default-temp-threshold}")
    private double defaultTempThreshold;

    @Value("${smartfarm.sensor.default-humidity-threshold}")
    private double defaultHumidityThreshold;

    @Cacheable(value = "deviceConfigObj", key = "#deviceId")
    public DeviceConfig getDeviceConfig(String deviceId) {
        log.info(">>> DB에서 {} 기기 설정값을 조회합니다. (이 로그가 보이면 캐시 미스 발생!)", deviceId);

        return deviceConfigRepository.findByDeviceId(deviceId)
                .orElseGet(() -> {
                    log.info(">>> 등록된 기기 설정이 없어 기본 설정값을 반환합니다. (온도: {}, 습도: {})", defaultTempThreshold, defaultHumidityThreshold);
                    return DeviceConfig.builder()
                            .deviceId(deviceId)
                            .temperatureThresholdHigh(defaultTempThreshold)
                            .humidityThresholdHigh(defaultHumidityThreshold)
                            .build();
                });
    }

    public List<DeviceConfigResponseDto> getAllDeviceConfigs() {
        return deviceConfigRepository.findAll().stream()
                .map(DeviceConfigResponseDto::from)
                .toList();
    }

    /** REST API(DeviceConfigController)에서 호출하는 저장/수정 메서드 */
    @Transactional
    @CacheEvict(value = "deviceConfigObj", key = "#request.deviceId")
    public DeviceConfig saveOrUpdateDeviceConfig(DeviceConfigRequestDto request) {
        log.info(">>> 기기 설정 저장/수정 및 캐시 삭제: {}", request.getDeviceId());

        DeviceConfig config = deviceConfigRepository.findByDeviceId(request.getDeviceId())
                .orElse(DeviceConfig.builder()
                        .deviceId(request.getDeviceId())
                        .temperatureThresholdHigh(request.getTemperatureThresholdHigh())
                        .humidityThresholdHigh(request.getHumidityThresholdHigh())
                        .build());

        if (config.getId() != null) {
            config.update(request.getTemperatureThresholdHigh(), request.getHumidityThresholdHigh());
        }

        return deviceConfigRepository.save(config);
    }

    /** 내부(SensorService)에서 호출하는 기존 저장 메서드 - 하위 호환 유지 */
    @Transactional
    @CacheEvict(value = "deviceConfigObj", key = "#deviceConfig.deviceId")
    public void saveOrUpdateDeviceConfig(DeviceConfig deviceConfig) {
        log.info(">>> 기기 설정 저장 및 캐시 삭제: {}", deviceConfig.getDeviceId());
        deviceConfigRepository.save(deviceConfig);
    }

    @Transactional
    @CacheEvict(value = "deviceConfigObj", key = "#deviceId")
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
     * PC 클라이언트 API 키 유효성 검증.
     * X-Device-Id, X-Api-Key 헤더 값을 검증합니다.
     *
     * <p>캐시된 {@link #getDeviceConfig(String)}를 사용하여 매 요청마다 DB를 조회하지 않습니다.</p>
     */
    public boolean validateApiKey(String deviceId, String apiKey) {
        DeviceConfig config = getDeviceConfig(deviceId);
        // apiKey가 null이면 미등록 기기(기본 설정 반환)이거나 키 발급 전 상태이므로 인증 거부
        return config.getApiKey() != null && config.getApiKey().equals(apiKey);
    }

    /**
     * API 키 재발급 — 기존 키를 새 UUID로 교체하고 캐시를 무효화합니다.
     */
    @Transactional
    @CacheEvict(value = "deviceConfigObj", key = "#deviceId")
    public DeviceConfigResponseDto regenerateApiKey(String deviceId) {
        DeviceConfig config = deviceConfigRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEVICE_NOT_FOUND));
        config.regenerateApiKey();
        DeviceConfig saved = deviceConfigRepository.save(config);
        log.info(">>> {} 기기 API 키 재발급 완료", deviceId);
        return DeviceConfigResponseDto.from(saved);
    }
}
