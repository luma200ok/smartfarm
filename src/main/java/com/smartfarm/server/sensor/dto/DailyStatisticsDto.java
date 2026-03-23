package com.smartfarm.server.sensor.dto;

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
public class DailyStatisticsDto {

    private LocalDate date;
    private Double avgTemperature;
    private Double maxTemperature;
    private Double minTemperature;
    private Double avgMemUsage;
    private Long dataCount;

    /** Lombok @Builder 용 전체 생성자 */
    public DailyStatisticsDto(LocalDate date, Double avgTemperature, Double maxTemperature,
                              Double minTemperature, Double avgMemUsage, Long dataCount) {
        this.date = date;
        this.avgTemperature = avgTemperature;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.avgMemUsage = avgMemUsage;
        this.dataCount = dataCount;
    }

    /**
     * QueryDSL Projections.constructor 전용 생성자.
     * MySQL DATE() 함수는 런타임에 java.sql.Date 를 반환하므로
     * 이 생성자에서 LocalDate 로 변환합니다.
     */
    public DailyStatisticsDto(java.sql.Date date, Double avgTemperature, Double maxTemperature,
                              Double minTemperature, Double avgMemUsage, Long dataCount) {
        this.date = date.toLocalDate();
        this.avgTemperature = avgTemperature;
        this.maxTemperature = maxTemperature;
        this.minTemperature = minTemperature;
        this.avgMemUsage = avgMemUsage;
        this.dataCount = dataCount;
    }
}
