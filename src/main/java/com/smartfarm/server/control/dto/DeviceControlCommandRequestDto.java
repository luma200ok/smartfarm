package com.smartfarm.server.control.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "원격 제어 명령 발송 요청 DTO")
public record DeviceControlCommandRequestDto(

        @NotBlank(message = "deviceId는 필수입니다.")
        @Schema(description = "제어 대상 기기 ID", example = "WINDOWS_PC_01")
        String deviceId,

        @NotBlank(message = "commandType은 필수입니다.")
        @Schema(description = "명령 종류 (COOLING_FAN_ON / COOLING_FAN_OFF / HUMIDIFIER_ON / HUMIDIFIER_OFF)",
                example = "COOLING_FAN_ON")
        String commandType
) {}
