package com.smartfarm.server.device.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceConfigRequestDto {

    @NotBlank(message = "디바이스 ID는 필수입니다.")
    private String deviceId;

    private Double temperatureThresholdHigh;
    private Double temperatureThresholdLow;
    private Double humidityThresholdHigh;
    private Double humidityThresholdLow;

    private String discordWebhookUrl;
}
