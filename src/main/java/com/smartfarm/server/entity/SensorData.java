package com.smartfarm.server.entity;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

import java.time.LocalDateTime;

/**
 * Redis에 저장될 센서 데이터 엔티티
 * 주의: @RedisHash의 timeToLive 값은 어노테이션 특성상 상수(숫자 리터럴)만 들어갈 수 있습니다.
 * (application.yaml의 값을 @Value로 직접 주입받을 수 없음)
 */
@Getter
@Builder
@RedisHash(value = "sensorData", timeToLive = 60) // 1분(60초) 스케줄러 간격에 맞춘 기본 TTL입니다.
public class SensorData {

    @Id // Redis Hash의 Key 역할 (예: sensorData:PC-01)
    private String deviceId;
    
    // 이전에는 cpuUsage라는 이름으로 온도를 저장하고 있었으므로, 의미를 명확히 하기 위해 temperature로 변경합니다.
    private double temperature; 
    
    // 이전에는 memoryUsage라는 이름으로 습도를 저장하고 있었으므로, 의미를 명확히 하기 위해 humidity로 변경합니다.
    private double humidity;    

    private LocalDateTime timestamp;
}
