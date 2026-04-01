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

    public void handle(SensorAlert alert) {
        // SSE 실시간 푸시는 경고 여부와 관계없이 항상 전송
        sendSsePush(alert);

        if (!alert.hasAlert()) {
            log.debug("경고 없음: {}", alert.getDeviceId());
            return;
        }

        String deviceId = alert.getDeviceId();

        // 1. 쿨링팬 제어
        if (alert.isCoolingFanOn()) {
            deviceControlService.sendAutoCommand(deviceId, "COOLING_FAN_ON");
            String msg = String.format("🚨 **[스마트팜 경고] %s 쿨링팬 가동!**\n%s", deviceId, alert.getCoolingMessage());
            discordNotificationService.sendAlertIfNotCoolingDown(deviceId, "TEMP_HIGH", msg);
        } else if (alert.isCoolingFanOff()) {
            deviceControlService.sendAutoCommand(deviceId, "COOLING_FAN_OFF");
        }

        // 2. 가습기 제어
        if (alert.isHumidifierOn()) {
            deviceControlService.sendAutoCommand(deviceId, "HUMIDIFIER_ON");
            String msg = String.format("💧 **[스마트팜 경고] %s 가습기 가동!**\n%s", deviceId, alert.getHumidifierMessage());
            discordNotificationService.sendAlertIfNotCoolingDown(deviceId, "HUMIDITY_HIGH", msg);
        } else if (alert.isHumidifierOff()) {
            deviceControlService.sendAutoCommand(deviceId, "HUMIDIFIER_OFF");
        }
    }

    private void sendSsePush(SensorAlert alert) {
        SsePayloadDto payload = SsePayloadDto.builder()
                .deviceId(alert.getDeviceId())
                .temperature(alert.getSensorData().getTemperature())
                .humidity(alert.getSensorData().getHumidity())
                .timestamp(alert.getSensorData().getTimestamp())
                .coolingFanOn(alert.isCoolingFanOn())
                .humidifierOn(alert.isHumidifierOn())
                .build();

        sseEmitterService.sendToDevice(alert.getDeviceId(), payload);
    }
}
