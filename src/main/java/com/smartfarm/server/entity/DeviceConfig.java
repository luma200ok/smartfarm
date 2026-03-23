package com.smartfarm.server.entity;

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
    private Double memUsageThresholdHigh; // 메모리 사용률 경보 임계치 (null = 전역 yaml 기본값 사용)

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
    public DeviceConfig(String deviceId, Double temperatureThresholdHigh, Double memUsageThresholdHigh) {
        this.deviceId = deviceId;
        this.temperatureThresholdHigh = temperatureThresholdHigh;
        this.memUsageThresholdHigh = memUsageThresholdHigh;
    }

    public void update(Double temperatureThresholdHigh, Double memUsageThresholdHigh, String discordWebhookUrl) {
        this.temperatureThresholdHigh = temperatureThresholdHigh;
        this.memUsageThresholdHigh    = memUsageThresholdHigh;
        this.discordWebhookUrl        = discordWebhookUrl;
    }

    public void setTemperatureThresholdHigh(Double temperatureThresholdHigh) {
        this.temperatureThresholdHigh = temperatureThresholdHigh;
    }

    public void setMemUsageThresholdHigh(Double memUsageThresholdHigh) {
        this.memUsageThresholdHigh = memUsageThresholdHigh;
    }

    public void setDiscordWebhookUrl(String discordWebhookUrl) {
        this.discordWebhookUrl = discordWebhookUrl;
    }

    /** API 키 재발급 */
    public void regenerateApiKey() {
        this.apiKey = UUID.randomUUID().toString();
    }
}
