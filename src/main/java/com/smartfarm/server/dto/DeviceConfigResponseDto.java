package com.smartfarm.server.dto;

import com.smartfarm.server.entity.DeviceConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceConfigResponseDto {

    private Long id;
    private String deviceId;
    private double temperatureThresholdHigh;
    private double humidityThresholdHigh;
    private String apiKey; // PC 클라이언트 인증용 API 키 (대시보드에서만 확인 가능)

    public static DeviceConfigResponseDto from(DeviceConfig entity) {
        return DeviceConfigResponseDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDeviceId())
                .temperatureThresholdHigh(entity.getTemperatureThresholdHigh())
                .humidityThresholdHigh(entity.getHumidityThresholdHigh())
                .apiKey(entity.getApiKey())
                .build();
    }
}
