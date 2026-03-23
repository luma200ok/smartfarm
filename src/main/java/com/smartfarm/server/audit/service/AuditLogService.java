package com.smartfarm.server.audit.service;

import com.smartfarm.server.audit.entity.AuditLog;
import com.smartfarm.server.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 감사 로그 서비스
 * 보안 이벤트(API 키 갱신, 인증 실패, 권한 거부 등)를 기록합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * API 키 생성 이벤트 기록
     */
    @Transactional
    public void logApiKeyGenerated(String deviceId, String performedBy, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .eventType("API_KEY_GENERATED")
                .deviceId(deviceId)
                .performedBy(performedBy)
                .details("새로운 API 키 생성")
                .success(true)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.info("[AUDIT] API Key Generated: deviceId={}, performedBy={}, ip={}", deviceId, performedBy, ipAddress);
    }

    /**
     * API 키 갱신 이벤트 기록
     */
    @Transactional
    public void logApiKeyRenewal(String deviceId, String performedBy, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .eventType("API_KEY_RENEWED")
                .deviceId(deviceId)
                .performedBy(performedBy)
                .details("API 키 갱신됨 (이전 키는 무효화)")
                .success(true)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.info("[AUDIT] API Key Renewed: deviceId={}, performedBy={}, ip={}", deviceId, performedBy, ipAddress);
    }

    /**
     * 인증 실패 이벤트 기록 (잘못된 API 키 등)
     */
    @Transactional
    public void logAuthFailure(String deviceId, String reason, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .eventType("AUTH_FAILURE")
                .deviceId(deviceId)
                .performedBy("UNKNOWN")
                .details("인증 실패: " + reason)
                .success(false)
                .errorMessage(reason)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.warn("[AUDIT] Authentication Failure: deviceId={}, reason={}, ip={}", deviceId, reason, ipAddress);
    }

    /**
     * 권한 거부 이벤트 기록
     */
    @Transactional
    public void logPermissionDenied(String deviceId, Long userId, String performedBy, String action, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .eventType("PERMISSION_DENIED")
                .userId(userId)
                .deviceId(deviceId)
                .performedBy(performedBy)
                .details("권한 거부: " + action)
                .success(false)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.warn("[AUDIT] Permission Denied: deviceId={}, userId={}, action={}, performer={}, ip={}",
                deviceId, userId, action, performedBy, ipAddress);
    }

    /**
     * 어드민 작업 기록 (사용자 생성, 삭제, 역할 변경 등)
     */
    @Transactional
    public void logAdminAction(String actionType, Long targetUserId, String performedBy, String details, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .eventType("ADMIN_ACTION")
                .userId(targetUserId)
                .deviceId("N/A")
                .performedBy(performedBy)
                .details(actionType + ": " + details)
                .success(true)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.info("[AUDIT] Admin Action: actionType={}, targetUserId={}, performer={}, ip={}",
                actionType, targetUserId, performedBy, ipAddress);
    }

    /**
     * 기기 설정 변경 기록
     */
    @Transactional
    public void logDeviceConfigChange(String deviceId, String performedBy, String changes, String ipAddress) {
        AuditLog auditLog = AuditLog.builder()
                .eventType("DEVICE_CONFIG_CHANGED")
                .deviceId(deviceId)
                .performedBy(performedBy)
                .details("기기 설정 변경: " + changes)
                .success(true)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(auditLog);
        log.info("[AUDIT] Device Config Changed: deviceId={}, performer={}, changes={}, ip={}",
                deviceId, performedBy, changes, ipAddress);
    }

    /**
     * 특정 기기의 감사 로그 조회
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByDeviceId(String deviceId, Pageable pageable) {
        return auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId, pageable);
    }

    /**
     * 특정 사용자의 감사 로그 조회
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByUserId(Long userId, Pageable pageable) {
        return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * 특정 이벤트 타입의 감사 로그 조회
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByEventType(String eventType, Pageable pageable) {
        return auditLogRepository.findByEventTypeOrderByCreatedAtDesc(eventType, pageable);
    }

    /**
     * 날짜 범위로 감사 로그 조회
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAuditLogsByDateRange(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        return auditLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end, pageable);
    }

    /**
     * 최근 인증 실패 기록 조회 (의심 활동 모니터링용)
     */
    @Transactional(readOnly = true)
    public List<AuditLog> getRecentAuthFailures(int hoursBack) {
        LocalDateTime since = LocalDateTime.now().minusHours(hoursBack);
        List<AuditLog> failedAttempts = auditLogRepository.findByEventTypeAndSuccessOrderByCreatedAtDesc("AUTH_FAILURE", false);
        return failedAttempts.stream()
                .filter(log -> log.getCreatedAt().isAfter(since))
                .toList();
    }
}
