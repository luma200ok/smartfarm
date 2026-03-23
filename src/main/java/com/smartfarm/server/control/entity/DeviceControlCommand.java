package com.smartfarm.server.control.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 대시보드에서 발송한 수동 제어 명령을 저장하는 엔티티.
 * PC 클라이언트가 주기적으로 PENDING 상태 명령을 폴링하여 실행한다.
 */
@Entity
@Table(name = "device_control_command")
@Getter
@NoArgsConstructor
public class DeviceControlCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 명령 대상 기기 ID */
    @Column(nullable = false)
    private String deviceId;

    /**
     * 명령 종류
     * COOLING_FAN_ON / COOLING_FAN_OFF / HEATER_ON / HEATER_OFF
     */
    @Column(nullable = false, length = 50)
    private String commandType;

    /** 명령 처리 상태 */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommandStatus status;

    /** 명령 생성(발송) 시각 */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** PC 클라이언트가 실행 확인한 시각 */
    private LocalDateTime acknowledgedAt;

    @Builder
    public DeviceControlCommand(String deviceId, String commandType, CommandStatus status, LocalDateTime createdAt) {
        this.deviceId = deviceId;
        this.commandType = commandType;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void acknowledge() {
        this.status = CommandStatus.ACKNOWLEDGED;
        this.acknowledgedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = CommandStatus.CANCELLED;
    }
}
