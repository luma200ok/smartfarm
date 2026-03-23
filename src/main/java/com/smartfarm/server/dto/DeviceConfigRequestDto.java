package com.smartfarm.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceConfigRequestDto {

    @NotBlank(message = "디바이스 ID는 필수입니다.")
    private String deviceId;

    @Positive(message = "온도 임계값은 양수여야 합니다.")
    private double temperatureThresholdHigh;

    @Positive(message = "메모리 사용률 임계값은 양수여야 합니다.")
    private double memUsageThresholdHigh;

    private String discordWebhookUrl;
}
