package com.smartfarm.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "원격 제어 명령 발송 요청 DTO")
public class DeviceControlCommandRequestDto {

    @Schema(description = "제어 대상 기기 ID", example = "WINDOWS_PC_01")
    private String deviceId;

    @Schema(description = "명령 종류 (COOLING_FAN_ON / COOLING_FAN_OFF / HEATER_ON / HEATER_OFF)",
            example = "COOLING_FAN_ON")
    private String commandType;
}
