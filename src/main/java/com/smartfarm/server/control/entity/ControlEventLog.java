package com.smartfarm.server.control.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 기기에 제어 명령(경보)이 내려간 이력을 저장하는 엔티티
 */
@Entity
@Table(name = "control_event_log")
@Getter
@NoArgsConstructor
public class ControlEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceId;

    // 이벤트 종류 (예: COOLING_FAN_ON, HEATER_ON)
    @Column(nullable = false, length = 50)
    private String eventType;

    // 발생 당시의 구체적인 상황 (예: "현재 온도 85도로 기준치(70도) 초과")
    @Column(length = 255)
    private String message;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Builder
    public ControlEventLog(String deviceId, String eventType, String message, LocalDateTime timestamp) {
        this.deviceId = deviceId;
        this.eventType = eventType;
        this.message = message;
        this.timestamp = timestamp;
    }
}