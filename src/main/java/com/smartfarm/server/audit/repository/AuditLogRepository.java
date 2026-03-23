package com.smartfarm.server.audit.repository;

import com.smartfarm.server.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * 특정 기기의 감사 로그 조회 (페이징)
     */
    Page<AuditLog> findByDeviceIdOrderByCreatedAtDesc(String deviceId, Pageable pageable);

    /**
     * 특정 사용자의 감사 로그 조회 (페이징)
     */
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 특정 이벤트 타입의 감사 로그 조회 (페이징)
     */
    Page<AuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType, Pageable pageable);

    /**
     * 기기와 이벤트 타입으로 조회 (페이징)
     */
    Page<AuditLog> findByDeviceIdAndEventTypeOrderByCreatedAtDesc(String deviceId, String eventType, Pageable pageable);

    /**
     * 날짜 범위로 조회 (페이징)
     */
    Page<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end, Pageable pageable);

    /**
     * 실패한 인증 시도 조회
     */
    List<AuditLog> findByEventTypeAndSuccessOrderByCreatedAtDesc(String eventType, Boolean success);

    /**
     * 최근 N시간의 로그 조회 (보안 모니터링용)
     */
    List<AuditLog> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime since);
}
