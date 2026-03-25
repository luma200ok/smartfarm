package com.smartfarm.server.service.event;

import com.smartfarm.server.dto.SsePayloadDto;
import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.repository.ControlEventLogRepository;
import com.smartfarm.server.service.DiscordNotificationService;
import com.smartfarm.server.service.SseEmitterService;
import com.smartfarm.server.service.strategy.SensorAlert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 센서 경고 이벤트 처리 (로깅, 알림, 실시간 푸시)
 * - 경고 발생 시 DB에 이벤트 로그 기록
 * - Discord 알림 발송
 * - SSE를 통한 실시간 클라이언트 푸시
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorAlertEventHandler {

    private final ControlEventLogRepository controlEventLogRepository;
    private final DiscordNotificationService discordNotificationService;
    private final SseEmitterService sseEmitterService;

    /**
     * 센서 경고 이벤트 처리
     * @param alert 센서 경고 상태
     */
    public void handle(SensorAlert alert) {
        if (!alert.hasAlert()) {
            log.debug("경고 없음: {}", alert.getDeviceId());
            return;
        }

        String deviceId = alert.getDeviceId();

        // 1. 쿨링팬 경고 처리
        if (alert.hasCoolingAlert()) {
            handleCoolingAlert(deviceId, alert.getCoolingMessage());
        }

        // 2. 히터 경고 처리
        if (alert.hasHeatingAlert()) {
            handleHeatingAlert(deviceId, alert.getHeatingMessage());
        }

        // 3. 실시간 SSE 푸시
        sendSsePush(alert);
    }

    private void handleCoolingAlert(String deviceId, String message) {
        // DB 이벤트 로그 기록
        saveEventLog(deviceId, "COOLING_FAN_ON", message);

        // Discord 알림
        String discordMsg = String.format("🚨 **[스마트팜 경고] %s 쿨링팬 가동!**\n%s", deviceId, message);
        discordNotificationService.sendMessage(discordMsg);
    }

    private void handleHeatingAlert(String deviceId, String message) {
        // DB 이벤트 로그 기록
        saveEventLog(deviceId, "HEATER_ON", message);

        // Discord 알림
        String discordMsg = String.format("💧 **[스마트팜 경고] %s 히터 가동!**\n%s", deviceId, message);
        discordNotificationService.sendMessage(discordMsg);
    }

    private void sendSsePush(SensorAlert alert) {
        SsePayloadDto payload = SsePayloadDto.builder()
                .deviceId(alert.getDeviceId())
                .temperature(alert.getSensorData().getTemperature())
                .humidity(alert.getSensorData().getHumidity())
                .timestamp(alert.getSensorData().getTimestamp())
                .coolingFanOn(alert.isCoolingFanOn())
                .heaterOn(alert.isHeaterOn())
                .build();

        sseEmitterService.sendToDevice(alert.getDeviceId(), payload);
    }

    private void saveEventLog(String deviceId, String eventType, String message) {
        ControlEventLog eventLog = ControlEventLog.builder()
                .deviceId(deviceId)
                .eventType(eventType)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();

        controlEventLogRepository.save(eventLog);
        log.info("이벤트 로그 저장: {} - {}", deviceId, eventType);
    }
}
