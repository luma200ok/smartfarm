package com.smartfarm.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 일별 센서 집계 통계 DTO
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyStatisticsDto {

    private LocalDate date;
    private Double avgTemperature;
    private Double maxTemperature;
    private Double minTemperature;
    private Double avgHumidity;
    private Long dataCount;
}
