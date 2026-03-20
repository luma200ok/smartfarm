package com.smartfarm.server.repository;

import com.smartfarm.server.entity.SensorHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * MySQL 데이터베이스 접근을 위한 JPA Repository
 * 기존 JpaRepository와 Querydsl로 만든 Custom Repository를 함께 상속받습니다.
 */
@Repository
public interface SensorHistoryRepository extends JpaRepository<SensorHistory, Long>, SensorHistoryRepositoryCustom {
    
    // 특정 디바이스의 데이터를 시간 내림차순(최신순)으로 페이징하여 조회
    Page<SensorHistory> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);
    
    // 특정 기간 동안의 특정 디바이스 데이터를 조회 (예: 오늘 하루치 데이터)
    Page<SensorHistory> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
            String deviceId, LocalDateTime start, LocalDateTime end, Pageable pageable);
}
