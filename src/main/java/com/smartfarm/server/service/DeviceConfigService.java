package com.smartfarm.server.service;

import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.repository.DeviceConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    @CacheEvict(value = "deviceConfigObj", key = "#deviceConfig.deviceId")
    public void saveOrUpdateDeviceConfig(DeviceConfig deviceConfig) {
        log.info(">>> 기기 설정 저장 및 캐시 삭제: {}", deviceConfig.getDeviceId());
        deviceConfigRepository.save(deviceConfig);
    }
}
