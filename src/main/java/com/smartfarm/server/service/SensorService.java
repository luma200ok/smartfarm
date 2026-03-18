package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.dto.SensorResponseDto;
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

    // 임계치 설정 (추후 DB나 설정 파일로 빼는 것이 좋습니다)
    private static final double TEMPERATURE_THRESHOLD_HIGH = 70.0; // 70도 이상이면 쿨링팬 가동

    public SensorResponseDto processSensorData(SensorRequestDto requestDto) {
        log.info("수신된 센서 데이터 확인: {}", requestDto);

        // 1. DTO -> Entity 변환 및 Redis 저장
        SensorData sensorData = requestDto.toEntity();
        sensorRepository.save(sensorData);
        log.info("Redis에 센서 데이터 저장 완료: {}", sensorData.getDeviceId());

        // 2. 비즈니스 로직 (역제어 명령 판단)
        boolean needCooling = sensorData.getTemperature() >= TEMPERATURE_THRESHOLD_HIGH;

        if (needCooling) {
            log.warn("🚨 {} 온도 경고! 쿨링팬 가동 명령 발행! (현재 온도: {}도)", 
                     sensorData.getDeviceId(), sensorData.getTemperature());
        }

        // 3. PC로 내려보낼 응답(명령) DTO 생성 및 반환
        return SensorResponseDto.builder()
                .status("SUCCESS")
                .message("Data processed successfully")
                .coolingFanOn(needCooling) // 온도가 높으면 true로 설정
                .heaterOn(false)           // 지금은 안 씀
                .build();
    }
}
