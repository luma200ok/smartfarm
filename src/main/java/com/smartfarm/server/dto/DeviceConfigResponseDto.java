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

    public static DeviceConfigResponseDto from(DeviceConfig entity) {
        return DeviceConfigResponseDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDeviceId())
                .temperatureThresholdHigh(entity.getTemperatureThresholdHigh())
                .humidityThresholdHigh(entity.getHumidityThresholdHigh())
                .build();
    }
}
