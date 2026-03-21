package com.smartfarm.server.service;

import com.smartfarm.server.dto.DeviceConfigRequestDto;
import com.smartfarm.server.dto.DeviceConfigResponseDto;
import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.repository.DeviceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceConfigService {

    private final DeviceConfigRepository deviceConfigRepository;

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
    }
}
