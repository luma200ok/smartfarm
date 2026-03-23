package com.smartfarm.server.controller;

import com.smartfarm.server.dto.CommandAckRequestDto;
import com.smartfarm.server.dto.DeviceControlCommandRequestDto;
import com.smartfarm.server.dto.DeviceControlCommandResponseDto;
import com.smartfarm.server.service.DeviceControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "4. 원격 제어 API",
        description = "대시보드에서 기기(쿨링팬/히터)를 수동으로 On/Off 제어하고, PC 클라이언트가 SSE 스트림으로 명령을 수신하는 API")
@RestController
@RequestMapping("/api/device-control")
@RequiredArgsConstructor
public class DeviceControlController {

    private final DeviceControlService deviceControlService;

    @Operation(summary = "수동 제어 명령 발송",
            description = "대시보드에서 쿨링팬 또는 히터를 수동으로 켜거나 끄는 명령을 발송합니다. "
                    + "명령 종류: COOLING_FAN_ON / COOLING_FAN_OFF / HEATER_ON / HEATER_OFF")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/command")
    public ResponseEntity<DeviceControlCommandResponseDto> sendCommand(
            @RequestBody DeviceControlCommandRequestDto request) {
        return ResponseEntity.ok(deviceControlService.sendCommand(request));
    }

    @Operation(summary = "PENDING 명령 플러시 (SSE 재연결 시 복구용)",
            description = "SSE 연결이 끊겨 있는 동안 DB에 누적된 PENDING 명령을 한 번에 가져옵니다. "
                    + "PC 클라이언트가 재시작 또는 재연결 직후 한 번만 호출하여 미처리 명령을 일괄 처리합니다. "
                    + "정상 운영 중에는 /api/sse/device-command-stream 을 통해 SSE로 명령을 수신합니다.")
    @GetMapping("/pending")
    public ResponseEntity<List<DeviceControlCommandResponseDto>> getPendingCommands(
            @Parameter(description = "기기 ID", example = "WINDOWS_PC_01")
            @RequestParam String deviceId) {
        return ResponseEntity.ok(deviceControlService.getPendingCommands(deviceId));
    }

    @Operation(summary = "명령 실행 확인 (PC 클라이언트 ACK)",
            description = "PC 클라이언트가 명령을 수신하고 실행한 후 서버에 완료를 알립니다.")
    @PostMapping("/ack")
    public ResponseEntity<DeviceControlCommandResponseDto> acknowledgeCommand(
            @RequestBody CommandAckRequestDto request) {
        return ResponseEntity.ok(deviceControlService.acknowledgeCommand(request));
    }

    @Operation(summary = "제어 명령 이력 조회",
            description = "특정 기기의 수동 제어 명령 이력을 최신순으로 페이징하여 반환합니다.")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/history")
    public ResponseEntity<Page<DeviceControlCommandResponseDto>> getCommandHistory(
            @Parameter(description = "기기 ID", example = "WINDOWS_PC_01")
            @RequestParam String deviceId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 보여줄 데이터 수", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(deviceControlService.getCommandHistory(deviceId, PageRequest.of(page, size)));
    }
}
