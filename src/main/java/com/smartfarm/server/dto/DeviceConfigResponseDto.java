package com.smartfarm.server.dto;

import com.smartfarm.server.entity.DeviceConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceConfigResponseDto {

    private Long id;
    private String deviceId;

    // 기기별 오버라이드 값 (null이면 전역 기본값 사용 중)
    private Double temperatureThresholdHigh;
    private Double memUsageThresholdHigh;

    // 전역 yaml 기본값 (프론트엔드 placeholder 표시용)
    private double globalTempThreshold;
    private double globalMemUsageThreshold;

    private String apiKey;
    private String discordWebhookUrl;

    public static DeviceConfigResponseDto from(DeviceConfig entity, double globalTempThreshold, double globalMemUsageThreshold) {
        return DeviceConfigResponseDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDeviceId())
                .temperatureThresholdHigh(entity.getTemperatureThresholdHigh())
                .memUsageThresholdHigh(entity.getMemUsageThresholdHigh())
                .globalTempThreshold(globalTempThreshold)
                .globalMemUsageThreshold(globalMemUsageThreshold)
                .apiKey(entity.getApiKey())
                .discordWebhookUrl(entity.getDiscordWebhookUrl())
                .build();
    }
}
