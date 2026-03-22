package com.smartfarm.server.service;

import com.smartfarm.server.dto.CommandAckRequestDto;
import com.smartfarm.server.dto.DeviceControlCommandRequestDto;
import com.smartfarm.server.dto.DeviceControlCommandResponseDto;
import com.smartfarm.server.entity.CommandStatus;
import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.entity.DeviceControlCommand;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.repository.ControlEventLogRepository;
import com.smartfarm.server.repository.DeviceControlCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceControlService {

    private static final Set<String> VALID_COMMAND_TYPES = Set.of(
            "COOLING_FAN_ON", "COOLING_FAN_OFF",
            "HEATER_ON", "HEATER_OFF"
    );

    /** 반대 명령 관계: ON → OFF, OFF → ON */
    private static final java.util.Map<String, String> OPPOSITE_COMMAND = java.util.Map.of(
            "COOLING_FAN_ON",  "COOLING_FAN_OFF",
            "COOLING_FAN_OFF", "COOLING_FAN_ON",
            "HEATER_ON",       "HEATER_OFF",
            "HEATER_OFF",      "HEATER_ON"
    );

    private final DeviceControlCommandRepository commandRepository;
    private final ControlEventLogRepository eventLogRepository;
    private final SseEmitterService sseEmitterService;

    /**
     * 대시보드에서 수동 제어 명령을 발송합니다.
     * - 같은 기기의 반대 명령이 PENDING 상태면 자동 취소합니다.
     * - ControlEventLog에 MANUAL_ 접두어로 이력을 기록합니다.
     */
    @Transactional
    public DeviceControlCommandResponseDto sendCommand(DeviceControlCommandRequestDto request) {
        String deviceId    = request.getDeviceId();
        String commandType = request.getCommandType();

        if (deviceId == null || deviceId.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (commandType == null || !VALID_COMMAND_TYPES.contains(commandType)) {
            throw new CustomException(ErrorCode.INVALID_COMMAND_TYPE);
        }

        // 반대 명령이 PENDING 상태면 취소
        String oppositeType = OPPOSITE_COMMAND.get(commandType);
        if (oppositeType != null) {
            List<DeviceControlCommand> opposite =
                    commandRepository.findByDeviceIdAndCommandTypeAndStatus(deviceId, oppositeType, CommandStatus.PENDING);
            opposite.forEach(DeviceControlCommand::cancel);
        }

        // 동일 명령이 이미 PENDING이면 중복 발송 방지 (취소 후 재생성)
        List<DeviceControlCommand> existing =
                commandRepository.findByDeviceIdAndCommandTypeAndStatus(deviceId, commandType, CommandStatus.PENDING);
        existing.forEach(DeviceControlCommand::cancel);

        // 새 명령 저장
        DeviceControlCommand command = DeviceControlCommand.builder()
                .deviceId(deviceId)
                .commandType(commandType)
                .status(CommandStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        commandRepository.save(command);

        // 제어 이벤트 로그 기록 (MANUAL_ 접두어)
        ControlEventLog eventLog = ControlEventLog.builder()
                .deviceId(deviceId)
                .eventType("MANUAL_" + commandType)
                .message("대시보드 수동 제어: " + commandType)
                .timestamp(LocalDateTime.now())
                .build();
        eventLogRepository.save(eventLog);

        DeviceControlCommandResponseDto response = DeviceControlCommandResponseDto.from(command);

        // PC 클라이언트가 SSE로 연결되어 있으면 즉시 푸시
        // 연결 안 되어 있어도 DB PENDING 상태로 유지되므로 클라이언트 재연결 시 폴링으로 수령 가능
        sseEmitterService.sendCommandToDevice(deviceId, response);

        return response;
    }

    /**
     * PC 클라이언트가 PENDING 명령 목록을 폴링합니다.
     */
    @Transactional(readOnly = true)
    public List<DeviceControlCommandResponseDto> getPendingCommands(String deviceId) {
        return commandRepository
                .findByDeviceIdAndStatusOrderByCreatedAtAsc(deviceId, CommandStatus.PENDING)
                .stream()
                .map(DeviceControlCommandResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * PC 클라이언트가 명령 실행 후 확인(ACK)을 보냅니다.
     */
    @Transactional
    public DeviceControlCommandResponseDto acknowledgeCommand(CommandAckRequestDto request) {
        DeviceControlCommand command = commandRepository.findById(request.getCommandId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMAND_NOT_FOUND));
        command.acknowledge();
        return DeviceControlCommandResponseDto.from(command);
    }

    /**
     * 대시보드에서 특정 기기의 제어 명령 이력을 페이징 조회합니다.
     */
    @Transactional(readOnly = true)
    public Page<DeviceControlCommandResponseDto> getCommandHistory(String deviceId, Pageable pageable) {
        return commandRepository
                .findByDeviceIdOrderByCreatedAtDesc(deviceId, pageable)
                .map(DeviceControlCommandResponseDto::from);
    }

    /**
     * 특정 기기의 모든 PENDING 명령을 취소합니다. (기기 삭제 시 등에 사용)
     */
    @Transactional
    public void cancelAllPending(String deviceId) {
        commandRepository.cancelAllPendingByDeviceId(deviceId);
    }
}
