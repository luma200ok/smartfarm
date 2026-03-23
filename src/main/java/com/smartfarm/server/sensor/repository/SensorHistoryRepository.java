package com.smartfarm.server.sensor.repository;

import com.smartfarm.server.sensor.entity.SensorHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MySQL 데이터베이스 접근을 위한 JPA Repository
 * 기존 JpaRepository와 Querydsl로 만든 Custom Repository를 함께 상속받습니다.
 */
@Repository
public interface SensorHistoryRepository extends JpaRepository<SensorHistory, Long>, SensorHistoryRepositoryCustom {

    // 특정 디바이스의 데이터를 시간 내림차순(최신순)으로 페이징하여 조회 (소프트 삭제 제외)
    Page<SensorHistory> findByDeviceIdAndDeletedAtIsNullOrderByTimestampDesc(String deviceId, Pageable pageable);

    // 특정 기간 동안의 특정 디바이스 데이터를 조회 (소프트 삭제 제외)
    Page<SensorHistory> findByDeviceIdAndTimestampBetweenAndDeletedAtIsNullOrderByTimestampDesc(
            String deviceId, LocalDateTime start, LocalDateTime end, Pageable pageable);

    // 실제 데이터를 전송한 기기 ID 목록을 중복 없이 조회 (소프트 삭제 제외)
    @Query("SELECT DISTINCT s.deviceId FROM SensorHistory s WHERE s.deletedAt IS NULL ORDER BY s.deviceId ASC")
    List<String> findDistinctDeviceIds();

    // 특정 기간 내 특정 기기 데이터를 시간 오름차순으로 전체 조회 (내보내기용, 소프트 삭제 제외)
    List<SensorHistory> findByDeviceIdAndTimestampBetweenAndDeletedAtIsNullOrderByTimestampAsc(
            String deviceId, LocalDateTime start, LocalDateTime end);

    // 1개월 이상 지난 데이터 삭제 (데이터 보존 정책)
    void deleteByTimestampBefore(LocalDateTime cutoff);

    // 기기 삭제 시 관련 데이터 소프트 딜리트
    @Modifying
    @Query("UPDATE SensorHistory s SET s.deletedAt = :deletedAt WHERE s.deviceId = :deviceId AND s.deletedAt IS NULL")
    void softDeleteByDeviceId(@Param("deviceId") String deviceId, @Param("deletedAt") LocalDateTime deletedAt);

    // 소프트 딜리트된 지 1주일 이상 지난 데이터 하드 삭제
    @Modifying
    @Query("DELETE FROM SensorHistory s WHERE s.deletedAt IS NOT NULL AND s.deletedAt < :cutoff")
    void deleteByDeletedAtBefore(@Param("cutoff") LocalDateTime cutoff);
}
