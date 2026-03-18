package com.smartfarm.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartfarm.server.entity.SensorData;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

    @NotBlank(message = "디바이스 ID는 필수입니다.")
    private String deviceId;
    
    // JSON의 스네이크 케이스("cpu_temperature")를 카멜 케이스 필드명에 매핑합니다.
    @JsonProperty("cpu_temperature")
    @Min(value = -50, message = "온도는 -50도 미만일 수 없습니다.") // 실제 온도로 가정하므로 범위를 변경합니다.
    @Max(value = 150, message = "온도는 150도를 초과할 수 없습니다.")
    private double cpuTemperature;
    
    // JSON의 스네이크 케이스("mem_usage")를 카멜 케이스 필드명에 매핑합니다.
    @JsonProperty("mem_usage")
    @Min(value = 0, message = "습도(메모리 사용률)는 0% 미만일 수 없습니다.")
    @Max(value = 100, message = "습도(메모리 사용률)는 100%를 초과할 수 없습니다.")
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
                .temperature(this.cpuTemperature) // Entity의 필드명 변경 반영
                .humidity(this.memUsage)          // Entity의 필드명 변경 반영
                .timestamp(convertedTimestamp)
                .build();
    }
}
