package com.smartfarm.server.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "PC 클라이언트 명령 실행 확인 요청 DTO")
public class CommandAckRequestDto {

    @Schema(description = "실행 확인할 명령 ID", example = "1")
    private Long commandId;
}
