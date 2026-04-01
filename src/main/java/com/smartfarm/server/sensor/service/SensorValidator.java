package com.smartfarm.server.sensor.service;

import com.smartfarm.server.sensor.dto.SensorRequestDto;
import com.smartfarm.server.common.exception.CustomException;
import com.smartfarm.server.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 센서 데이터 유효성 검사 담당
 * - 온도, 습도 범위 검증 (YAML 설정 기반)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorValidator {

    @Value("${smartfarm.sensor.validation.temp-min}")
    private double tempMin;

    @Value("${smartfarm.sensor.validation.temp-max}")
    private double tempMax;

    @Value("${smartfarm.sensor.validation.humidity-min}")
    private double humidityMin;

    @Value("${smartfarm.sensor.validation.humidity-max}")
    private double humidityMax;

    public void validate(SensorRequestDto requestDto) {
        validateTemperature(requestDto.getCpuTemperature());
        validateHumidity(requestDto.getHumidity());
    }

    private void validateTemperature(double temperature) {
        if (temperature < tempMin || temperature > tempMax) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("온도는 %.1f도에서 %.1f도 사이여야 합니다. (입력값: %.1f)",
                            tempMin, tempMax, temperature));
        }
    }

    private void validateHumidity(double humidity) {
        if (humidity < humidityMin || humidity > humidityMax) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    String.format("습도는 %.1f%%에서 %.1f%% 사이여야 합니다. (입력값: %.1f)",
                            humidityMin, humidityMax, humidity));
        }
    }
}
