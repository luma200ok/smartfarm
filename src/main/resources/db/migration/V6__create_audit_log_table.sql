-- 감사 로그 테이블 생성
-- API 키 갱신, 인증 실패, 권한 거부, 어드민 작업 등 보안 관련 이벤트를 기록합니다.

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(50) NOT NULL COMMENT '감사 이벤트 타입 (API_KEY_GENERATED, API_KEY_RENEWED, AUTH_FAILURE, PERMISSION_DENIED, ADMIN_ACTION, DEVICE_CONFIG_CHANGED)',
    user_id BIGINT COMMENT '관련 사용자 ID (PC 클라이언트의 경우 null)',
    device_id VARCHAR(100) NOT NULL COMMENT '관련 기기 ID',
    performed_by VARCHAR(100) NOT NULL COMMENT '작업 수행자 (사용자명 또는 "SYSTEM")',
    details TEXT COMMENT '작업 내용 및 상세 정보',
    success BOOLEAN NOT NULL DEFAULT TRUE COMMENT '성공 여부 (true=성공, false=실패)',
    error_message TEXT COMMENT '에러 메시지 (실패 시에만 기록)',
    ip_address VARCHAR(45) COMMENT '실행 IP 주소',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '이벤트 발생 시간',
    PRIMARY KEY (id),
    INDEX idx_device_id (device_id),
    INDEX idx_user_id (user_id),
    INDEX idx_event_type (event_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='보안 감사 로그 테이블';
