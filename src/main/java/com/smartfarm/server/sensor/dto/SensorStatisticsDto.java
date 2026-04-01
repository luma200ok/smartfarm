package com.smartfarm.server.sensor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Querydsl 통계 쿼리 결과를 담을 DTO입니다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorStatisticsDto {
    private String deviceId;
    private double maxTemperature;
    private double minTemperature;
    private double avgTemperature;
    private double avgHumidity;
}
