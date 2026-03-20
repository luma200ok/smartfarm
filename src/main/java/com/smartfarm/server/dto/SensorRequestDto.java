package com.smartfarm.server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartfarm.server.entity.SensorData;
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
    // 참고: DTO 필드의 @Min, @Max 어노테이션은 컴파일 시점에 상수(고정값)만 허용하므로
    // application.yaml의 값을 @Value로 동적으로 주입받아 사용할 수 없습니다.
    // 대신 Service 계층에서 커스텀 검증 로직을 통해 yaml 설정값으로 유효성 검사를 수행하도록 변경합니다.
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
                .temperature(this.cpuTemperature) // Entity의 필드명 변경 반영
                .humidity(this.memUsage)          // Entity의 필드명 변경 반영
                .timestamp(convertedTimestamp)
                .build();
    }
}