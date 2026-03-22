package com.smartfarm.server.entity;

/**
 * 원격 제어 명령의 처리 상태
 */
public enum CommandStatus {
    /** 발송됨 — PC 클라이언트가 아직 실행하지 않은 상태 */
    PENDING,

    /** 실행 확인됨 — PC 클라이언트가 명령을 수신하고 처리 완료한 상태 */
    ACKNOWLEDGED,

    /** 취소됨 — 동일 기기에 반대 명령이 들어오거나 수동으로 취소된 상태 */
    CANCELLED
}
