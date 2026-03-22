package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.dto.SensorResponseDto;
import com.smartfarm.server.dto.SsePayloadDto;
import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.entity.SensorData;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.repository.SensorRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorRedisRepository sensorRepository;
    private final DeviceConfigService deviceConfigService;
    private final DiscordNotificationService discordNotificationService;
    private final SseEmitterService sseEmitterService;
    private final DeviceControlService deviceControlService;

    // application.yaml에서 유효성 검사 기준값을 가져옵니다.
    @Value("${smartfarm.sensor.validation.temp-min}")
    private double tempMin;

    @Value("${smartfarm.sensor.validation.temp-max}")
    private double tempMax;

    @Value("${smartfarm.sensor.validation.humidity-min}")
    private double humidityMin;

    @Value("${smartfarm.sensor.validation.humidity-max}")
    private double humidityMax;

    public SensorResponseDto processSensorData(SensorRequestDto requestDto) {
        log.info("수신된 센서 데이터 확인: {}", requestDto);

        // 1. 데이터 유효성 검사 (yaml 설정값 기반)
        validateSensorData(requestDto);

        // 2. DTO -> Entity 변환 및 Redis 저장
        SensorData sensorData = requestDto.toEntity();
        sensorRepository.save(sensorData);
        log.info("Redis에 센서 데이터 저장 완료: {}", sensorData.getDeviceId());

        // 3. 기기별 설정값 조회
        DeviceConfig config = deviceConfigService.getDeviceConfig(sensorData.getDeviceId());

        // 4. 비즈니스 로직 (임계값 초과 여부 판단)
        boolean needCooling         = sensorData.getTemperature() >= config.getTemperatureThresholdHigh();
        boolean needHumidityControl = sensorData.getHumidity()    >= config.getHumidityThresholdHigh();

        // 5. 경고 발생 시 자동 제어 명령 발송 + DB 이력 기록 + 디스코드 알림
        if (needCooling) {
            String message = String.format("현재 온도: %.1f도, 설정 기준치: %.1f도",
                                           sensorData.getTemperature(), config.getTemperatureThresholdHigh());
            log.warn("🚨 {} 온도 경고! 쿨링팬 가동 명령 발행! ({})", sensorData.getDeviceId(), message);

            deviceControlService.sendAutoCommand(sensorData.getDeviceId(), "COOLING_FAN_ON");

            String discordMsg = String.format("🚨 **[스마트팜 경고] %s 쿨링팬 가동!**\n%s", sensorData.getDeviceId(), message);
            discordNotificationService.sendMessage(discordMsg);
        }

        if (needHumidityControl) {
            String message = String.format("현재 습도: %.1f%%, 설정 기준치: %.1f%%",
                                           sensorData.getHumidity(), config.getHumidityThresholdHigh());
            log.warn("🚨 {} 습도 경고! 히터 가동 명령 발행! ({})", sensorData.getDeviceId(), message);

            deviceControlService.sendAutoCommand(sensorData.getDeviceId(), "HEATER_ON");

            String discordMsg = String.format("💧 **[스마트팜 경고] %s 히터 가동!**\n%s", sensorData.getDeviceId(), message);
            discordNotificationService.sendMessage(discordMsg);
        }

        // 6. SSE로 실시간 데이터 push
        sseEmitterService.sendToDevice(sensorData.getDeviceId(), SsePayloadDto.builder()
                .deviceId(sensorData.getDeviceId())
                .temperature(sensorData.getTemperature())
                .humidity(sensorData.getHumidity())
                .timestamp(sensorData.getTimestamp())
                .coolingFanOn(needCooling)
                .heaterOn(needHumidityControl)
                .build());

        // 7. 응답 반환
        return SensorResponseDto.builder()
                .status("SUCCESS")
                .message("Data processed successfully")
                .coolingFanOn(needCooling)
                .heaterOn(needHumidityControl)
                .build();
    }

    /**
     * yaml 설정값을 기반으로 동적 유효성 검사를 수행합니다.
     */
    private void validateSensorData(SensorRequestDto requestDto) {
        if (requestDto.getCpuTemperature() < tempMin || requestDto.getCpuTemperature() > tempMax) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, 
                    String.format("온도는 %.1f도에서 %.1f도 사이여야 합니다. (입력값: %.1f)",
                            tempMin, tempMax, requestDto.getCpuTemperature()));
        }

        if (requestDto.getMemUsage() < humidityMin || requestDto.getMemUsage() > humidityMax) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, 
                    String.format("습도는 %.1f%%에서 %.1f%% 사이여야 합니다. (입력값: %.1f)",
                            humidityMin, humidityMax, requestDto.getMemUsage()));
        }
    }

}
