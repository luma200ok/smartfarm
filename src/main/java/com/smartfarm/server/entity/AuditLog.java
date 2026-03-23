package com.smartfarm.server.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 보안 감시 로그 엔티티
 * API 키 갱신, 인증 실패, 권한 거부, 어드민 작업 등을 기록합니다.
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_device_id", columnList = "device_id"),
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_event_type", columnList = "event_type"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
@Getter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 감사 이벤트 타입
     * API_KEY_GENERATED, API_KEY_RENEWED, AUTH_FAILURE, PERMISSION_DENIED, ADMIN_ACTION
     */
    @Column(nullable = false, length = 50)
    private String eventType;

    /**
     * 관련 사용자 ID (null 가능 - PC 클라이언트의 경우)
     */
    @Column(nullable = true)
    private Long userId;

    /**
     * 관련 기기 ID
     */
    @Column(nullable = false, length = 100)
    private String deviceId;

    /**
     * 작업 수행자 (사용자명 또는 "SYSTEM")
     */
    @Column(nullable = false, length = 100)
    private String performedBy;

    /**
     * 작업 내용 및 상세 정보
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * 성공 여부 (true = 성공, false = 실패)
     */
    @Column(nullable = false)
    private Boolean success;

    /**
     * 에러 메시지 (실패 시에만 기록)
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * 실행 IP 주소
     */
    @Column(length = 45)
    private String ipAddress;

    /**
     * 이벤트 발생 시간
     */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public AuditLog(String eventType, Long userId, String deviceId, String performedBy,
                    String details, Boolean success, String errorMessage, String ipAddress) {
        this.eventType = eventType;
        this.userId = userId;
        this.deviceId = deviceId;
        this.performedBy = performedBy;
        this.details = details;
        this.success = success;
        this.errorMessage = errorMessage;
        this.ipAddress = ipAddress;
        this.createdAt = LocalDateTime.now();
    }
}
