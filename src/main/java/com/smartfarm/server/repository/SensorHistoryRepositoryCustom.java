package com.smartfarm.server.repository;

import com.smartfarm.server.dto.DailyStatisticsDto;
import com.smartfarm.server.dto.SensorStatisticsDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Querydsl을 사용한 커스텀 쿼리 메서드들을 정의하는 인터페이스입니다.
 */
public interface SensorHistoryRepositoryCustom {

    // 특정 기기의 특정 기간 동안의 통계(최고, 최저, 평균 온도 및 습도)를 조회합니다.
    SensorStatisticsDto getSensorStatistics(String deviceId, LocalDateTime start, LocalDateTime end);

    // 여러 기기의 특정 기간 동안의 통계를 한 번의 쿼리로 조회합니다. (N+1 방지)
    Map<String, SensorStatisticsDto> getAllDevicesStatistics(List<String> deviceIds, LocalDateTime start, LocalDateTime end);

    // 특정 기기의 특정 기간을 일별로 집계한 통계 목록을 조회합니다.
    List<DailyStatisticsDto> getDailyStatistics(String deviceId, LocalDateTime start, LocalDateTime end);
}
