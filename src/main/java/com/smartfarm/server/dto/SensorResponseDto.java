package com.smartfarm.server.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 서버가 센서(PC)로 내려보내는 응답 및 제어 명령 DTO
 */
@Getter
@Builder
public class SensorResponseDto {
    private String status;      // "SUCCESS", "ERROR" 등
    private String message;     // 상세 메시지
    private boolean coolingFanOn; // 쿨링 팬 가동 여부 (역제어 명령)
    private boolean heaterOn;     // 히터 가동 여부 (역제어 명령)
}
