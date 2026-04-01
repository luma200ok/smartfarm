package com.smartfarm.server.device.dto;

import com.smartfarm.server.device.entity.DeviceConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceConfigResponseDto {

    private Long id;
    private String deviceId;

    // 기기별 오버라이드 값 (null이면 전역 기본값 사용 중)
    private Double temperatureThresholdHigh;
    private Double temperatureThresholdLow;
    private Double humidityThresholdHigh;
    private Double humidityThresholdLow;

    // 전역 yaml 기본값 (프론트엔드 placeholder 표시용)
    private double globalTempThresholdHigh;
    private double globalTempThresholdLow;
    private double globalHumidityThresholdHigh;
    private double globalHumidityThresholdLow;

    private String apiKey;
    private String discordWebhookUrl;

    public static DeviceConfigResponseDto from(DeviceConfig entity,
                                               double globalTempHigh, double globalTempLow,
                                               double globalHumidityHigh, double globalHumidityLow) {
        return DeviceConfigResponseDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDeviceId())
                .temperatureThresholdHigh(entity.getTemperatureThresholdHigh())
                .temperatureThresholdLow(entity.getTemperatureThresholdLow())
                .humidityThresholdHigh(entity.getHumidityThresholdHigh())
                .humidityThresholdLow(entity.getHumidityThresholdLow())
                .globalTempThresholdHigh(globalTempHigh)
                .globalTempThresholdLow(globalTempLow)
                .globalHumidityThresholdHigh(globalHumidityHigh)
                .globalHumidityThresholdLow(globalHumidityLow)
                .apiKey(entity.getApiKey())
                .discordWebhookUrl(entity.getDiscordWebhookUrl())
                .build();
    }

    /**
     * {@link DeviceConfigView}로부터 응답 DTO를 생성합니다.
     * view 에는 이미 null 임계값이 전역 기본값으로 채워져 있으므로 해당 값을 그대로 사용합니다.
     */
    public static DeviceConfigResponseDto from(DeviceConfigView view,
                                               double globalTempHigh, double globalTempLow,
                                               double globalHumidityHigh, double globalHumidityLow) {
        return DeviceConfigResponseDto.builder()
                .deviceId(view.deviceId())
                .temperatureThresholdHigh(view.temperatureThresholdHigh())
                .temperatureThresholdLow(view.temperatureThresholdLow())
                .humidityThresholdHigh(view.humidityThresholdHigh())
                .humidityThresholdLow(view.humidityThresholdLow())
                .globalTempThresholdHigh(globalTempHigh)
                .globalTempThresholdLow(globalTempLow)
                .globalHumidityThresholdHigh(globalHumidityHigh)
                .globalHumidityThresholdLow(globalHumidityLow)
                .apiKey(view.apiKey())
                .discordWebhookUrl(view.discordWebhookUrl())
                .build();
    }
}
