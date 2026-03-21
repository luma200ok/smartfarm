package com.smartfarm.server.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * SSE로 클라이언트에게 실시간 전송하는 센서 데이터 페이로드
 */
@Getter
@Builder
public class SsePayloadDto {
    private String deviceId;
    private double temperature;
    private double humidity;
    private LocalDateTime timestamp;
    private boolean coolingFanOn;
    private boolean heaterOn;
}
