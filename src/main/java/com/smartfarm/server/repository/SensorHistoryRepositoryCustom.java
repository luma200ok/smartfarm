package com.smartfarm.server.repository;

import com.smartfarm.server.dto.SensorStatisticsDto;

import java.time.LocalDateTime;

/**
 * Querydsl을 사용한 커스텀 쿼리 메서드들을 정의하는 인터페이스입니다.
 */
public interface SensorHistoryRepositoryCustom {
    
    // 특정 기기의 특정 기간 동안의 통계(최고, 최저, 평균 온도 및 습도)를 조회합니다.
    SensorStatisticsDto getSensorStatistics(String deviceId, LocalDateTime start, LocalDateTime end);
}
