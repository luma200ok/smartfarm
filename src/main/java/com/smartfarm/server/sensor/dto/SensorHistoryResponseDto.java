package com.smartfarm.server.sensor.dto;

import com.smartfarm.server.sensor.entity.SensorHistory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 대시보드 조회를 위한 응답 DTO
 * MySQL(SensorHistory)의 데이터를 클라이언트에게 반환할 때 사용합니다.
 */
@Getter
@Builder
public class SensorHistoryResponseDto {
    private Long id;
    private String deviceId;
    private double temperature;
    private double memUsage;
    private LocalDateTime timestamp;

    /**
     * Entity를 DTO로 변환하는 정적 팩토리 메서드
     */
    public static SensorHistoryResponseDto from(SensorHistory entity) {
        return SensorHistoryResponseDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDeviceId())
                .temperature(entity.getTemperature())
                .memUsage(entity.getMemUsage())
                .timestamp(entity.getTimestamp())
                .build();
    }
}
