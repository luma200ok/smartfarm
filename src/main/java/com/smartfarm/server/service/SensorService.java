package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.dto.SensorResponseDto;
import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.entity.SensorData;
import com.smartfarm.server.repository.ControlEventLogRepository;
import com.smartfarm.server.repository.SensorRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRedisRepository sensorRepository;
    private final DeviceConfigService deviceConfigService;
    private final ControlEventLogRepository controlEventLogRepository; // 이벤트 로그 레포지토리 주입

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

        // 4. 경고 발생 시 DB에 영구 기록 (이벤트 로깅)
        if (needCooling) {
            String message = String.format("현재 온도: %.1f도, 설정 기준치: %.1f도", 
                                           sensorData.getTemperature(), config.getTemperatureThresholdHigh());
            log.warn("🚨 {} 온도 경고! 쿨링팬 가동 명령 발행! ({})", sensorData.getDeviceId(), message);
            
            // DB에 기록
            saveEventLog(sensorData.getDeviceId(), "COOLING_FAN_ON", message);
        }

        if (needHeater) {
            String message = String.format("현재 습도: %.1f%%, 설정 기준치: %.1f%%", 
                                           sensorData.getHumidity(), config.getHumidityThresholdHigh());
            log.warn("🚨 {} 습도 경고! 히터 가동 명령 발행! ({})", sensorData.getDeviceId(), message);
            
            // DB에 기록
            saveEventLog(sensorData.getDeviceId(), "HEATER_ON", message);
        }

        // 5. PC로 내려보낼 응답(명령) DTO 생성 및 반환
        return SensorResponseDto.builder()
                .status("SUCCESS")
                .message("Data processed successfully")
                .coolingFanOn(needCooling)
                .heaterOn(needHeater)
                .build();
    }

    /**
     * 경보 이력을 DB에 저장하는 내부 헬퍼 메서드
     */
    private void saveEventLog(String deviceId, String eventType, String message) {
        ControlEventLog eventLog = ControlEventLog.builder()
                .deviceId(deviceId)
                .eventType(eventType)
                .message(message)
                .timestamp(LocalDateTime.now()) // 이벤트가 발생한 현재 서버 시간
                .build();
                
        controlEventLogRepository.save(eventLog);
    }
}
