package com.smartfarm.server.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

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

    @Column(nullable = false)
    private double temperatureThresholdHigh; // 고온 경보 임계치 (이 온도 이상이면 쿨링팬 가동)

    @Column(nullable = false)
    private double humidityThresholdHigh; // 고습 경보 임계치 (추후 확장을 위해 미리 추가)

    @Builder
    public DeviceConfig(String deviceId, double temperatureThresholdHigh, double humidityThresholdHigh) {
        this.deviceId = deviceId;
        this.temperatureThresholdHigh = temperatureThresholdHigh;
        this.humidityThresholdHigh = humidityThresholdHigh;
    }
}