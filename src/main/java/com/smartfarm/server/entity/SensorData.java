package com.smartfarm.server.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

/**
 * Redis에 저장될 센서 데이터 엔티티
 */
@Getter
@Builder
@RedisHash(value = "sensorData", timeToLive = 60) // 실시간 최신 데이터를 위해 60초 TTL(Time To Live) 설정
public class SensorData {

    @Id // Redis Hash의 Key 역할 (예: sensorData:PC-01)
    private String deviceId;
    
    private double cpuUsage;
    private double memoryUsage;
    private LocalDateTime timestamp;
}
