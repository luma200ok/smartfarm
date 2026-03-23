package com.smartfarm.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기기별 오늘 통계 비교 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceComparisonDto {

    private String deviceId;
    private Double maxTemperature;
    private Double minTemperature;
    private Double avgTemperature;
    private Double avgMemUsage;
    private double tempThreshold;
    private double memUsageThreshold;
}
