package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.dto.SensorResponseDto;
import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.entity.SensorData;
import com.smartfarm.server.repository.SensorRedisRepository;
import com.smartfarm.server.service.event.SensorAlertEventHandler;
import com.smartfarm.server.service.strategy.SensorAlert;
import com.smartfarm.server.service.strategy.SensorControlStrategy;
import com.smartfarm.server.service.validator.SensorValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Thin Service Pattern 적용
 * - 데이터 유효성 검사: SensorValidator
 * - 비즈니스 로직 (역제어 판단): SensorControlStrategy
 * - 이벤트 처리 (로깅, 알림, SSE): SensorAlertEventHandler
 *
 * Service는 순수하게 '조정자(Orchestrator)' 역할만 수행
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRedisRepository sensorRepository;
    private final DeviceConfigService deviceConfigService;
    private final SensorValidator sensorValidator;
    private final SensorControlStrategy sensorControlStrategy;
    private final SensorAlertEventHandler sensorAlertEventHandler;

    public SensorResponseDto processSensorData(SensorRequestDto requestDto) {
        log.info("수신된 센서 데이터 확인: {}", requestDto);

        // 1. 데이터 유효성 검사
        sensorValidator.validate(requestDto);

        // 2. DTO -> Entity 변환 및 Redis 저장
        SensorData sensorData = requestDto.toEntity();
        sensorRepository.save(sensorData);
        log.info("Redis에 센서 데이터 저장 완료: {}", sensorData.getDeviceId());

        // 3. 기기별 설정값 조회
        DeviceConfig config = deviceConfigService.getDeviceConfig(sensorData.getDeviceId());

        // 4. 역제어 명령 판단
        SensorAlert alert = sensorControlStrategy.determineControl(sensorData, config);

        // 5. 경고 이벤트 처리 (로깅, 알림, SSE 푸시)
        sensorAlertEventHandler.handle(alert);

        // 6. 응답 반환
        return SensorResponseDto.builder()
                .status("SUCCESS")
                .message("Data processed successfully")
                .coolingFanOn(alert.isCoolingFanOn())
                .heaterOn(alert.isHeaterOn())
                .build();
    }
}
