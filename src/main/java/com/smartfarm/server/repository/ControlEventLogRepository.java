package com.smartfarm.server.repository;

import com.smartfarm.server.entity.ControlEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ControlEventLogRepository extends JpaRepository<ControlEventLog, Long> {

    Page<ControlEventLog> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);

    // 특정 기기의 기간 내 알림 발생 횟수 조회
    long countByDeviceIdAndTimestampBetween(String deviceId, LocalDateTime start, LocalDateTime end);
}