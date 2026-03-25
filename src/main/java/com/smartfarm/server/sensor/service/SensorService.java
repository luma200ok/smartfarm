package com.smartfarm.server.sensor.service;

import com.smartfarm.server.device.dto.DeviceConfigView;
import com.smartfarm.server.sensor.dto.SensorRequestDto;
import com.smartfarm.server.sensor.dto.SensorResponseDto;
import com.smartfarm.server.sensor.entity.SensorData;
import com.smartfarm.server.sensor.repository.SensorRedisRepository;
import com.smartfarm.server.device.service.DeviceConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 센서 데이터 처리 서비스 (Thin Service Pattern)
 * - 유효성 검사, 비즈니스 로직, 이벤트 처리를 전문 컴포넌트에 위임
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

    @Transactional
    public SensorResponseDto processSensorData(SensorRequestDto requestDto) {
        log.info("수신된 센서 데이터 확인: {}", requestDto);

        // 1. 데이터 유효성 검사 (YAML 설정값 기반)
        sensorValidator.validate(requestDto);

        // 2. DTO -> Entity 변환 및 Redis 저장
        SensorData sensorData = requestDto.toEntity();
        sensorRepository.save(sensorData);
        log.info("Redis에 센서 데이터 저장 완료: {}", sensorData.getDeviceId());

        // 3. 기기별 설정값 조회
        DeviceConfigView config = deviceConfigService.getDeviceConfig(sensorData.getDeviceId());

        // 4. 제어 판단 (임계값 비교 및 경고 생성)
        SensorAlert alert = sensorControlStrategy.determineControl(sensorData, config);

        // 5. 경고 처리 (제어 명령 발송, 알림, SSE 푸시)
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
