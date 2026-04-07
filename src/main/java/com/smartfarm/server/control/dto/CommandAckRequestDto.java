package com.smartfarm.server.control.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "PC 클라이언트 명령 실행 확인 요청 DTO")
public record CommandAckRequestDto(

        @NotNull(message = "commandId는 필수입니다.")
        @Schema(description = "실행 확인할 명령 ID", example = "1")
        Long commandId
) {}
