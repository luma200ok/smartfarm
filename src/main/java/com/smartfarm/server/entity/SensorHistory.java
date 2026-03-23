package com.smartfarm.server.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * MySQL에 영구 저장될 센서 이력 데이터 엔티티 (JPA Entity)
 */
@Entity
@Table(name = "sensor_history")
@Getter
@NoArgsConstructor // JPA는 기본 생성자가 필요합니다.
public class SensorHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // MySQL의 Auto Increment PK

    @Column(nullable = false)
    private String deviceId;

    @Column(nullable = false)
    private double temperature;

    @Column(nullable = false)
    private double memUsage;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private LocalDateTime deletedAt;

    @Builder
    public SensorHistory(String deviceId, double temperature, double memUsage, LocalDateTime timestamp) {
        this.deviceId = deviceId;
        this.temperature = temperature;
        this.memUsage = memUsage;
        this.timestamp = timestamp;
    }
}
