package com.smartfarm.server.control.dto;

import com.smartfarm.server.control.entity.CommandStatus;
import com.smartfarm.server.control.entity.DeviceControlCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "원격 제어 명령 응답 DTO")
public class DeviceControlCommandResponseDto {

    @Schema(description = "명령 ID")
    private Long id;

    @Schema(description = "기기 ID")
    private String deviceId;

    @Schema(description = "명령 종류")
    private String commandType;

    @Schema(description = "처리 상태 (PENDING / ACKNOWLEDGED / CANCELLED)")
    private CommandStatus status;

    @Schema(description = "명령 생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "실행 확인 시각 (미실행 시 null)")
    private LocalDateTime acknowledgedAt;

    public static DeviceControlCommandResponseDto from(DeviceControlCommand entity) {
        return DeviceControlCommandResponseDto.builder()
                .id(entity.getId())
                .deviceId(entity.getDeviceId())
                .commandType(entity.getCommandType())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .acknowledgedAt(entity.getAcknowledgedAt())
                .build();
    }
}
