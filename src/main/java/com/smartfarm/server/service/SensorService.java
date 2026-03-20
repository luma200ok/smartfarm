package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.dto.SensorResponseDto;
import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.entity.SensorData;
import com.smartfarm.server.repository.SensorRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRedisRepository sensorRepository;
    private final DeviceConfigService deviceConfigService;

    public SensorResponseDto processSensorData(SensorRequestDto requestDto) {
        log.info("수신된 센서 데이터 확인: {}", requestDto);

        // 1. DTO -> Entity 변환 및 Redis 저장 (빠른 쓰기)
        SensorData sensorData = requestDto.toEntity();
        sensorRepository.save(sensorData);
        log.info("Redis에 센서 데이터 저장 완료: {}", sensorData.getDeviceId());

        // 2. 기기별 설정값(온도, 습도 임계치)을 한 번에 조회합니다.
        DeviceConfig config = deviceConfigService.getDeviceConfig(sensorData.getDeviceId());

        // 3. 비즈니스 로직 (역제어 명령 판단)
        boolean needCooling = sensorData.getTemperature() >= config.getTemperatureThresholdHigh();
        boolean needHeater = sensorData.getHumidity() >= config.getHumidityThresholdHigh();

        if (needCooling) {
            log.warn("🚨 {} 온도 경고! 쿨링팬 가동 명령 발행! (현재 온도: {}도, 설정 기준치: {}도)", 
                     sensorData.getDeviceId(), sensorData.getTemperature(), config.getTemperatureThresholdHigh());
        }

        if (needHeater) {
            log.warn("🚨 {} 습도 경고! 히터 가동 명령 발행! (현재 습도: {}%, 설정 기준치: {}%)", 
                     sensorData.getDeviceId(), sensorData.getHumidity(), config.getHumidityThresholdHigh());
        }

        // 4. PC로 내려보낼 응답(명령) DTO 생성 및 반환
        return SensorResponseDto.builder()
                .status("SUCCESS")
                .message("Data processed successfully")
                .coolingFanOn(needCooling)
                .heaterOn(needHeater)
                .build();
    }
}
