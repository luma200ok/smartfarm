package com.smartfarm.server.service;

import com.smartfarm.server.entity.AuditLog;
import com.smartfarm.server.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * AuditLogService 단위 테스트
 * 모든 감사 로그 기록 기능을 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuditLogServiceTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private static final String TEST_DEVICE_ID = "TEST-DEVICE-001";
    private static final Long TEST_USER_ID = 123L;
    private static final String PERFORMER = "test-user";
    private static final String TEST_IP = "192.168.1.100";

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void testLogApiKeyGenerated() {
        // When: API 키 생성 이벤트 기록
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP);

        // Then: 감사 로그가 저장되었는지 확인
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("API_KEY_GENERATED");
        assertThat(log.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(log.getPerformedBy()).isEqualTo(PERFORMER);
        assertThat(log.getSuccess()).isTrue();
        assertThat(log.getIpAddress()).isEqualTo(TEST_IP);
        assertThat(log.getDetails()).contains("새로운 API 키 생성");
    }

    @Test
    void testLogApiKeyRenewal() {
        // When: API 키 갱신 이벤트 기록
        auditLogService.logApiKeyRenewal(TEST_DEVICE_ID, PERFORMER, TEST_IP);

        // Then: 감사 로그가 저장되었는지 확인
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("API_KEY_RENEWED");
        assertThat(log.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(log.getPerformedBy()).isEqualTo(PERFORMER);
        assertThat(log.getSuccess()).isTrue();
        assertThat(log.getDetails()).contains("API 키 갱신됨");
    }

    @Test
    void testLogAuthFailure() {
        // When: 인증 실패 이벤트 기록
        String reason = "Invalid API key provided";
        auditLogService.logAuthFailure(TEST_DEVICE_ID, reason, TEST_IP);

        // Then: 감사 로그가 저장되었는지 확인
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("AUTH_FAILURE");
        assertThat(log.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(log.getPerformedBy()).isEqualTo("UNKNOWN");
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getErrorMessage()).contains(reason);
        assertThat(log.getIpAddress()).isEqualTo(TEST_IP);
    }

    @Test
    void testLogPermissionDenied() {
        // When: 권한 거부 이벤트 기록
        String action = "Device access denied";
        auditLogService.logPermissionDenied(TEST_DEVICE_ID, TEST_USER_ID, PERFORMER, action, TEST_IP);

        // Then: 감사 로그가 저장되었는지 확인
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("PERMISSION_DENIED");
        assertThat(log.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(log.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(log.getPerformedBy()).isEqualTo(PERFORMER);
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getDetails()).contains(action);
        assertThat(log.getIpAddress()).isEqualTo(TEST_IP);
    }

    @Test
    void testLogAdminAction() {
        // When: 어드민 작업 이벤트 기록
        String actionType = "USER_CREATION";
        String details = "Created user 'newuser'";
        auditLogService.logAdminAction(actionType, TEST_USER_ID, PERFORMER, details, TEST_IP);

        // Then: 감사 로그가 저장되었는지 확인
        List<AuditLog> logs = auditLogRepository.findByUserIdOrderByCreatedAtDesc(TEST_USER_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("ADMIN_ACTION");
        assertThat(log.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(log.getPerformedBy()).isEqualTo(PERFORMER);
        assertThat(log.getSuccess()).isTrue();
        assertThat(log.getDetails()).contains(actionType);
        assertThat(log.getIpAddress()).isEqualTo(TEST_IP);
    }

    @Test
    void testLogDeviceConfigChange() {
        // When: 기기 설정 변경 이벤트 기록
        String changes = "temperatureThreshold=65.0, memUsageThreshold=70.0, webhookUrl=***";
        auditLogService.logDeviceConfigChange(TEST_DEVICE_ID, PERFORMER, changes, TEST_IP);

        // Then: 감사 로그가 저장되었는지 확인
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("DEVICE_CONFIG_CHANGED");
        assertThat(log.getDeviceId()).isEqualTo(TEST_DEVICE_ID);
        assertThat(log.getPerformedBy()).isEqualTo(PERFORMER);
        assertThat(log.getSuccess()).isTrue();
        assertThat(log.getDetails()).contains("기기 설정 변경");
        assertThat(log.getDetails()).contains(changes);
    }

    @Test
    void testGetAuditLogsByDeviceId() {
        // Given: 특정 기기의 여러 감사 로그 생성
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP);
        auditLogService.logApiKeyRenewal(TEST_DEVICE_ID, PERFORMER, TEST_IP);
        auditLogService.logAuthFailure(TEST_DEVICE_ID, "Invalid key", TEST_IP);

        // When: 기기별 감사 로그 조회
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> logs = auditLogService.getAuditLogsByDeviceId(TEST_DEVICE_ID, pageable);

        // Then: 모든 로그가 조회되어야 함
        assertThat(logs.getContent()).hasSize(3);
        assertThat(logs.getContent().get(0).getEventType()).isEqualTo("AUTH_FAILURE"); // 최신순
        assertThat(logs.getContent().get(1).getEventType()).isEqualTo("API_KEY_RENEWED");
        assertThat(logs.getContent().get(2).getEventType()).isEqualTo("API_KEY_GENERATED");
    }

    @Test
    void testGetAuditLogsByUserId() {
        // Given: 특정 사용자의 여러 감사 로그 생성
        auditLogService.logAdminAction("USER_CREATION", TEST_USER_ID, PERFORMER, "Created user", TEST_IP);
        auditLogService.logAdminAction("USER_DELETION", TEST_USER_ID, PERFORMER, "Deleted user", TEST_IP);

        // When: 사용자별 감사 로그 조회
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> logs = auditLogService.getAuditLogsByUserId(TEST_USER_ID, pageable);

        // Then: 모든 로그가 조회되어야 함
        assertThat(logs.getContent()).hasSize(2);
        assertThat(logs.getContent()).allMatch(log -> log.getUserId().equals(TEST_USER_ID));
    }

    @Test
    void testGetAuditLogsByEventType() {
        // Given: 다양한 이벤트 타입의 감사 로그 생성
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP);
        auditLogService.logApiKeyGenerated("OTHER-DEVICE", PERFORMER, TEST_IP);
        auditLogService.logAuthFailure(TEST_DEVICE_ID, "Invalid key", TEST_IP);

        // When: 특정 이벤트 타입의 감사 로그 조회
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> logs = auditLogService.getAuditLogsByEventType("API_KEY_GENERATED", pageable);

        // Then: 해당 타입의 로그만 조회되어야 함
        assertThat(logs.getContent()).hasSize(2);
        assertThat(logs.getContent()).allMatch(log -> log.getEventType().equals("API_KEY_GENERATED"));
    }

    @Test
    void testGetAuditLogsByDateRange() {
        // Given: 감사 로그 생성
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourBefore = now.minusHours(1);
        LocalDateTime oneHourAfter = now.plusHours(1);

        // When: 날짜 범위로 감사 로그 조회
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> logsInRange = auditLogService.getAuditLogsByDateRange(oneHourBefore, oneHourAfter, pageable);
        Page<AuditLog> logsOutOfRange = auditLogService.getAuditLogsByDateRange(oneHourAfter, oneHourAfter.plusHours(1), pageable);

        // Then: 범위 내의 로그만 조회되어야 함
        assertThat(logsInRange.getContent()).hasSize(1);
        assertThat(logsOutOfRange.getContent()).isEmpty();
    }

    @Test
    void testGetRecentAuthFailures() {
        // Given: 인증 실패 로그 생성
        auditLogService.logAuthFailure(TEST_DEVICE_ID, "Invalid key", TEST_IP);
        auditLogService.logAuthFailure("OTHER-DEVICE", "Missing headers", TEST_IP);
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP); // 성공 로그 (포함 안됨)

        // When: 최근 1시간의 인증 실패 조회
        List<AuditLog> recentFailures = auditLogService.getRecentAuthFailures(1);

        // Then: 인증 실패 로그만 조회되어야 함
        assertThat(recentFailures).hasSize(2);
        assertThat(recentFailures).allMatch(log -> log.getEventType().equals("AUTH_FAILURE"));
        assertThat(recentFailures).allMatch(log -> !log.getSuccess());
    }

    @Test
    void testMultipleEventsForSameDevice() {
        // Given: 같은 기기에 대한 여러 이벤트
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP);
        auditLogService.logDeviceConfigChange(TEST_DEVICE_ID, PERFORMER, "Config changed", TEST_IP);
        auditLogService.logAuthFailure(TEST_DEVICE_ID, "Invalid key", TEST_IP);

        // When: 기기별 감사 로그 조회
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> logs = auditLogService.getAuditLogsByDeviceId(TEST_DEVICE_ID, pageable);

        // Then: 모든 이벤트가 기록되어야 함
        assertThat(logs.getContent()).hasSize(3);
        assertThat(logs.getTotalElements()).isEqualTo(3);
    }

    @Test
    void testAuditLogTimestamp() {
        // When: 감사 로그 생성
        LocalDateTime beforeLogging = LocalDateTime.now();
        auditLogService.logApiKeyGenerated(TEST_DEVICE_ID, PERFORMER, TEST_IP);
        LocalDateTime afterLogging = LocalDateTime.now();

        // Then: 타임스탐프가 정확하게 기록되어야 함
        AuditLog log = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 1)).getContent().get(0);
        assertThat(log.getCreatedAt()).isNotNull();
        assertThat(log.getCreatedAt()).isAfterOrEqualTo(beforeLogging);
        assertThat(log.getCreatedAt()).isBeforeOrEqualTo(afterLogging);
    }

    @Test
    void testAuditLogWithNullValues() {
        // When: 선택적 필드가 null인 감사 로그 생성
        auditLogService.logPermissionDenied(TEST_DEVICE_ID, null, PERFORMER, "Access denied", TEST_IP);

        // Then: null 값이 올바르게 저장되어야 함
        AuditLog log = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 1)).getContent().get(0);
        assertThat(log.getUserId()).isNull();
        assertThat(log.getDeviceId()).isNotNull();
        assertThat(log.getDetails()).isNotNull();
    }
}
