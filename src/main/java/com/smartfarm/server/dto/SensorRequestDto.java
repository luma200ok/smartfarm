package com.smartfarm.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartfarm.server.entity.SensorData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 센서(PC)로부터 수신할 데이터 전송 객체 (DTO)
 */
@Getter
@NoArgsConstructor
@ToString
public class SensorRequestDto {
    private String deviceId;
    
    // JSON의 스네이크 케이스("cpu_temperature")를 카멜 케이스 필드명에 매핑합니다.
    @JsonProperty("cpu_temperature")
    private double cpuTemperature;
    
    // JSON의 스네이크 케이스("mem_usage")를 카멜 케이스 필드명에 매핑합니다.
    @JsonProperty("mem_usage")
    private double memUsage;
    
    private long timestamp;

    /**
     * DTO 객체 자신을 Entity 객체로 변환하는 메서드.
     * @return SensorData 엔티티
     */
    public SensorData toEntity() {
        LocalDateTime convertedTimestamp = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(this.timestamp),
                ZoneId.of("Asia/Seoul")
        );

        return SensorData.builder()
                .deviceId(this.deviceId)
                .cpuUsage(this.cpuTemperature)
                .memoryUsage(this.memUsage)
                .timestamp(convertedTimestamp)
                .build();
    }
}
