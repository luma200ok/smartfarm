package com.smartfarm.server.control.service;

import com.smartfarm.server.control.dto.CommandAckRequestDto;
import com.smartfarm.server.control.dto.DeviceControlCommandRequestDto;
import com.smartfarm.server.control.dto.DeviceControlCommandResponseDto;
import com.smartfarm.server.control.entity.CommandStatus;
import com.smartfarm.server.control.entity.ControlEventLog;
import com.smartfarm.server.control.entity.DeviceControlCommand;
import com.smartfarm.server.common.exception.CustomException;
import com.smartfarm.server.common.exception.ErrorCode;
import com.smartfarm.server.control.repository.ControlEventLogRepository;
import com.smartfarm.server.control.repository.DeviceControlCommandRepository;
import com.smartfarm.server.dashboard.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceControlService {

    private static final Set<String> VALID_COMMAND_TYPES = Set.of(
            "COOLING_FAN_ON", "COOLING_FAN_OFF",
            "HEATER_ON", "HEATER_OFF",
            "HUMIDIFIER_ON", "HUMIDIFIER_OFF"
    );

    /** 반대 명령 관계: ON → OFF, OFF → ON */
    private static final java.util.Map<String, String> OPPOSITE_COMMAND = java.util.Map.of(
            "COOLING_FAN_ON",  "COOLING_FAN_OFF",
            "COOLING_FAN_OFF", "COOLING_FAN_ON",
            "HEATER_ON",       "HEATER_OFF",
            "HEATER_OFF",      "HEATER_ON",
            "HUMIDIFIER_ON",   "HUMIDIFIER_OFF",
            "HUMIDIFIER_OFF",  "HUMIDIFIER_ON"
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
        String deviceId    = request.deviceId();
        String commandType = request.commandType();

        if (!VALID_COMMAND_TYPES.contains(commandType)) {
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

        // 트랜잭션 커밋 완료 후 SSE 푸시 (레이스 컨디션 방지)
        // sensor_agent가 동일 EC2에서 실행되므로 ACK가 커밋 전에 도달할 수 있음
        // → acknowledgeCommand()의 findById()가 미커밋 행을 못 읽어 COMMAND_NOT_FOUND 발생 방지
        final long commandId = command.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean pushed = sseEmitterService.sendCommandToDevice(deviceId, response);
                if (!pushed) {
                    log.info("[DeviceControl] {} SSE 미연결 — 명령(id={}) DB PENDING 상태로 보존됩니다.", deviceId, commandId);
                }
            }
        });

        return response;
    }

    /**
     * 임계값 초과 시 자동 제어 명령을 발송합니다.
     * - 동일 명령이 이미 PENDING이면 중복 발송을 건너뜁니다 (수동 제어와 달리 재생성하지 않음).
     * - 반대 명령이 PENDING이면 자동 취소합니다.
     * - ControlEventLog에 AUTO_ 접두어로 이력을 기록합니다.
     */
    @Transactional
    public void sendAutoCommand(String deviceId, String commandType) {
        // 동일 명령이 이미 PENDING이면 중복 발송 생략
        List<DeviceControlCommand> existing =
                commandRepository.findByDeviceIdAndCommandTypeAndStatus(deviceId, commandType, CommandStatus.PENDING);
        if (!existing.isEmpty()) {
            log.debug("[AutoControl] {} {} 이미 PENDING 상태 — 중복 발송 생략", deviceId, commandType);
            return;
        }

        // 반대 명령이 PENDING 상태면 취소
        String oppositeType = OPPOSITE_COMMAND.get(commandType);
        if (oppositeType != null) {
            List<DeviceControlCommand> opposite =
                    commandRepository.findByDeviceIdAndCommandTypeAndStatus(deviceId, oppositeType, CommandStatus.PENDING);
            opposite.forEach(DeviceControlCommand::cancel);
        }

        // 새 명령 저장
        DeviceControlCommand command = DeviceControlCommand.builder()
                .deviceId(deviceId)
                .commandType(commandType)
                .status(CommandStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        commandRepository.save(command);

        // 제어 이벤트 로그 기록 (AUTO_ 접두어)
        ControlEventLog eventLog = ControlEventLog.builder()
                .deviceId(deviceId)
                .eventType("AUTO_" + commandType)
                .message("임계값 초과 자동 제어: " + commandType)
                .timestamp(LocalDateTime.now())
                .build();
        eventLogRepository.save(eventLog);

        DeviceControlCommandResponseDto response = DeviceControlCommandResponseDto.from(command);

        // 트랜잭션 커밋 완료 후 SSE 푸시 (레이스 컨디션 방지)
        final long commandId = command.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean pushed = sseEmitterService.sendCommandToDevice(deviceId, response);
                if (!pushed) {
                    log.info("[AutoControl] {} SSE 미연결 — 자동 명령(id={}) DB PENDING 상태로 보존됩니다.", deviceId, commandId);
                }
            }
        });
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
        DeviceControlCommand command = commandRepository.findById(request.commandId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMAND_NOT_FOUND));
        command.acknowledge();
        DeviceControlCommandResponseDto response = DeviceControlCommandResponseDto.from(command);
        // ACK 완료 상태를 대시보드에 실시간 푸시
        sseEmitterService.sendCommandAckToDashboard(command.getDeviceId(), response);
        return response;
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

    /**
     * 특정 기기의 현재 쿨링팬/히터/가습기 상태를 반환합니다.
     * 가장 최근 ACKNOWLEDGED 명령 타입으로 ON/OFF 여부를 판단합니다.
     * 대시보드 페이지 새로고침 시 표시등 초기화에 사용됩니다.
     */
    @Transactional(readOnly = true)
    public Map<String, Boolean> getDeviceState(String deviceId) {
        List<String> allTypes = List.of(
                "COOLING_FAN_ON", "COOLING_FAN_OFF",
                "HEATER_ON", "HEATER_OFF",
                "HUMIDIFIER_ON", "HUMIDIFIER_OFF"
        );
        // 기기 유형 3종 × ON/OFF 2종 = 최대 6건으로 상태 판단에 충분
        List<DeviceControlCommand> commands = commandRepository
                .findAllAcknowledgedByDeviceIdAndTypes(deviceId, CommandStatus.ACKNOWLEDGED, allTypes, PageRequest.of(0, 6));

        boolean coolingFanOn = commands.stream()
                .filter(c -> c.getCommandType().startsWith("COOLING_FAN"))
                .map(c -> "COOLING_FAN_ON".equals(c.getCommandType()))
                .findFirst()
                .orElse(false);

        boolean heaterOn = commands.stream()
                .filter(c -> c.getCommandType().startsWith("HEATER"))
                .map(c -> "HEATER_ON".equals(c.getCommandType()))
                .findFirst()
                .orElse(false);

        boolean humidifierOn = commands.stream()
                .filter(c -> c.getCommandType().startsWith("HUMIDIFIER"))
                .map(c -> "HUMIDIFIER_ON".equals(c.getCommandType()))
                .findFirst()
                .orElse(false);

        return Map.of("coolingFanOn", coolingFanOn, "heaterOn", heaterOn, "humidifierOn", humidifierOn);
    }
}
