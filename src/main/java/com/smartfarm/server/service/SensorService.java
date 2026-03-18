package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorRequestDto;
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

    public void processSensorData(SensorRequestDto requestDto) {
        log.info("수신된 센서 데이터 확인: {}", requestDto);

        // DTO 내부의 변환 로직(toEntity)을 호출하여 Entity로 변환합니다.
        // 서비스 계층은 데이터 저장(비즈니스 로직)에만 집중할 수 있게 되어 코드가 간결해집니다.
        SensorData sensorData = requestDto.toEntity();

        // Spring Data Redis(JPA 스타일)를 이용한 데이터 저장
        sensorRepository.save(sensorData);
        log.info("Redis에 센서 데이터 저장 완료: {}", sensorData.getDeviceId());
    }
}