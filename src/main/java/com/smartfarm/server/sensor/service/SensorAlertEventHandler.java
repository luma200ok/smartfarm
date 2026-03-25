package com.smartfarm.server.sensor.service;

import com.smartfarm.server.dashboard.dto.SsePayloadDto;
import com.smartfarm.server.control.service.DeviceControlService;
import com.smartfarm.server.notification.service.DiscordNotificationService;
import com.smartfarm.server.dashboard.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 센서 경고 이벤트 처리 (로깅, 알림, 실시간 푸시)
 * - 경고 발생 시 자동 제어 명령 발송
 * - Discord 알림 발송
 * - SSE를 통한 실시간 클라이언트 푸시
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SensorAlertEventHandler {

    private final DeviceControlService deviceControlService;
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
        // 자동 제어 명령 발송
        deviceControlService.sendAutoCommand(deviceId, "COOLING_FAN_ON");

        // Discord 알림
        String discordMsg = String.format("🚨 **[스마트팜 경고] %s 쿨링팬 가동!**\n%s", deviceId, message);
        discordNotificationService.sendAlertIfNotCoolingDown(deviceId, "TEMP", discordMsg);
    }

    private void handleHeatingAlert(String deviceId, String message) {
        // 자동 제어 명령 발송
        deviceControlService.sendAutoCommand(deviceId, "HEATER_ON");

        // Discord 알림
        String discordMsg = String.format("💧 **[스마트팜 경고] %s 히터 가동!**\n%s", deviceId, message);
        discordNotificationService.sendAlertIfNotCoolingDown(deviceId, "HUMIDITY", discordMsg);
    }

    private void sendSsePush(SensorAlert alert) {
        SsePayloadDto payload = SsePayloadDto.builder()
                .deviceId(alert.getDeviceId())
                .temperature(alert.getSensorData().getTemperature())
                .memUsage(alert.getSensorData().getMemUsage())
                .timestamp(alert.getSensorData().getTimestamp())
                .coolingFanOn(alert.isCoolingFanOn())
                .heaterOn(alert.isHeaterOn())
                .build();

        sseEmitterService.sendToDevice(alert.getDeviceId(), payload);
    }
}
