package com.smartfarm.server.device.dto;

import com.smartfarm.server.device.entity.DeviceConfig;

/**
 * {@link DeviceConfig} 엔티티를 JPA 컨텍스트 밖에서 안전하게 다루기 위한 불변 뷰 객체.
 *
 * <p>null 임계값은 생성 시점에 전역 yaml 기본값으로 채워지므로,
 * 이 객체를 캐시하거나 트랜잭션 외부에서 사용해도 Hibernate 더티 체킹이 발생하지 않습니다.</p>
 */
public record DeviceConfigView(
        String deviceId,
        Double temperatureThresholdHigh,
        Double temperatureThresholdLow,
        Double humidityThresholdHigh,
        Double humidityThresholdLow,
        String apiKey,
        String discordWebhookUrl
) {

    /**
     * {@link DeviceConfig} 엔티티와 전역 기본값으로부터 뷰 객체를 생성합니다.
     * null 임계값은 전역 기본값으로 채워집니다.
     */
    public static DeviceConfigView from(DeviceConfig entity,
                                        double defaultTempHigh, double defaultTempLow,
                                        double defaultHumidityHigh, double defaultHumidityLow) {
        return new DeviceConfigView(
                entity.getDeviceId(),
                entity.getTemperatureThresholdHigh() != null ? entity.getTemperatureThresholdHigh() : defaultTempHigh,
                entity.getTemperatureThresholdLow()  != null ? entity.getTemperatureThresholdLow()  : defaultTempLow,
                entity.getHumidityThresholdHigh()    != null ? entity.getHumidityThresholdHigh()    : defaultHumidityHigh,
                entity.getHumidityThresholdLow()     != null ? entity.getHumidityThresholdLow()     : defaultHumidityLow,
                entity.getApiKey(),
                entity.getDiscordWebhookUrl()
        );
    }
}
