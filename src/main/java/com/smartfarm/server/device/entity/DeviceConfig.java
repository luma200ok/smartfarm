package com.smartfarm.server.device.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "device_config")
@Getter
@NoArgsConstructor
public class DeviceConfig implements Serializable {

    // Redis 직렬화를 위한 고유 버전 ID (선택 사항이지만 권장됨)
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String deviceId; // 기기 고유 ID (예: SENSOR-001)

    @Column(nullable = true)
    private Double temperatureThresholdHigh; // 고온 경보 임계치 (null = 전역 yaml 기본값 사용)

    @Column(nullable = true)
    private Double temperatureThresholdLow; // 저온 쿨링팬 OFF 임계치

    @Column(nullable = true)
    private Double humidityThresholdHigh; // 고습 가습기 ON 임계치

    @Column(nullable = true)
    private Double humidityThresholdLow; // 저습 가습기 OFF 임계치

    @Column(nullable = false, unique = true)
    private String apiKey; // PC 클라이언트 인증용 API 키 (UUID)

    @Column(nullable = true)
    private String discordWebhookUrl;

    /** 신규 등록 시 API 키 자동 생성 */
    @PrePersist
    public void generateApiKeyIfAbsent() {
        if (this.apiKey == null) {
            this.apiKey = UUID.randomUUID().toString();
        }
    }

    @Builder
    public DeviceConfig(String deviceId, Double temperatureThresholdHigh, Double temperatureThresholdLow,
                        Double humidityThresholdHigh, Double humidityThresholdLow) {
        this.deviceId = deviceId;
        this.temperatureThresholdHigh = temperatureThresholdHigh;
        this.temperatureThresholdLow  = temperatureThresholdLow;
        this.humidityThresholdHigh    = humidityThresholdHigh;
        this.humidityThresholdLow     = humidityThresholdLow;
    }

    public void update(Double temperatureThresholdHigh, Double temperatureThresholdLow,
                       Double humidityThresholdHigh, Double humidityThresholdLow, String discordWebhookUrl) {
        this.temperatureThresholdHigh = temperatureThresholdHigh;
        this.temperatureThresholdLow  = temperatureThresholdLow;
        this.humidityThresholdHigh    = humidityThresholdHigh;
        this.humidityThresholdLow     = humidityThresholdLow;
        this.discordWebhookUrl        = discordWebhookUrl;
    }

    public void setTemperatureThresholdHigh(Double temperatureThresholdHigh) {
        this.temperatureThresholdHigh = temperatureThresholdHigh;
    }

    public void setTemperatureThresholdLow(Double temperatureThresholdLow) {
        this.temperatureThresholdLow = temperatureThresholdLow;
    }

    public void setHumidityThresholdHigh(Double humidityThresholdHigh) {
        this.humidityThresholdHigh = humidityThresholdHigh;
    }

    public void setHumidityThresholdLow(Double humidityThresholdLow) {
        this.humidityThresholdLow = humidityThresholdLow;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setDiscordWebhookUrl(String discordWebhookUrl) {
        this.discordWebhookUrl = discordWebhookUrl;
    }

    /**
     * API 키 재발급 — 평문 UUID를 생성합니다.
     * 암호화는 {@code DeviceConfigService} 레이어에서 처리합니다.
     *
     * @return 새로 생성된 평문 UUID (암호화 전)
     */
    public String regenerateApiKey() {
        String newKey = UUID.randomUUID().toString();
        this.apiKey = newKey;
        return newKey;
    }
}
