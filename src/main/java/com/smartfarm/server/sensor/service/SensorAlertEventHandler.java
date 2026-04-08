package com.smartfarm.server.sensor.service;

import com.smartfarm.server.dashboard.dto.SsePayloadDto;
import com.smartfarm.server.control.service.DeviceControlService;
import com.smartfarm.server.notification.service.DiscordNotificationService;
import com.smartfarm.server.dashboard.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

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

    public void handle(SensorAlert alert, Map<String, Boolean> deviceState) {
        // SSE 실시간 푸시는 경고 여부와 관계없이 항상 전송
        // 알림이 없는 구간에서도 실제 기기 ON/OFF 상태를 대시보드에 반영
        sendSsePush(alert, deviceState);

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
        } else if (alert.isCoolingFanMidOff()) {
            deviceControlService.sendAutoCommand(deviceId, "COOLING_FAN_OFF");
        }

        // 2. 히터 제어
        if (alert.isHeaterOn()) {
            deviceControlService.sendAutoCommand(deviceId, "HEATER_ON");
            String msg = String.format("🔥 **[스마트팜 경고] %s 히터 가동!**\n%s", deviceId, alert.getHeaterMessage());
            discordNotificationService.sendAlertIfNotCoolingDown(deviceId, "TEMP_LOW", msg);
        } else if (alert.isHeaterOff()) {
            deviceControlService.sendAutoCommand(deviceId, "HEATER_OFF");
        } else if (alert.isHeaterMidOff()) {
            deviceControlService.sendAutoCommand(deviceId, "HEATER_OFF");
        }

        // 3. 가습기 제어
        if (alert.isHumidifierOn()) {
            deviceControlService.sendAutoCommand(deviceId, "HUMIDIFIER_ON");
            String msg = String.format("💧 **[스마트팜 경고] %s 가습기 가동!**\n%s", deviceId, alert.getHumidifierMessage());
            discordNotificationService.sendAlertIfNotCoolingDown(deviceId, "HUMIDITY_LOW", msg);
        } else if (alert.isHumidifierOff()) {
            deviceControlService.sendAutoCommand(deviceId, "HUMIDIFIER_OFF");
        } else if (alert.isHumidifierMidOff()) {
            deviceControlService.sendAutoCommand(deviceId, "HUMIDIFIER_OFF");
        }
    }

    private void sendSsePush(SensorAlert alert, Map<String, Boolean> deviceState) {
        // 이번 사이클에 alert가 발생하면 alert 결과를 우선 반영 (즉각적인 UI 피드백)
        // alert 없는 구간에는 DB 기반 실제 기기 상태(deviceState)를 사용
        boolean fanOn;
        if (alert.isCoolingFanOn()) {
            fanOn = true;
        } else if (alert.isCoolingFanOff() || alert.isCoolingFanMidOff()) {
            fanOn = false;
        } else {
            fanOn = deviceState.getOrDefault("coolingFanOn", false);
        }

        boolean htrOn;
        if (alert.isHeaterOn()) {
            htrOn = true;
        } else if (alert.isHeaterOff() || alert.isHeaterMidOff()) {
            htrOn = false;
        } else {
            htrOn = deviceState.getOrDefault("heaterOn", false);
        }

        boolean humiOn;
        if (alert.isHumidifierOn()) {
            humiOn = true;
        } else if (alert.isHumidifierOff() || alert.isHumidifierMidOff()) {
            humiOn = false;
        } else {
            humiOn = deviceState.getOrDefault("humidifierOn", false);
        }

        SsePayloadDto payload = SsePayloadDto.builder()
                .deviceId(alert.getDeviceId())
                .temperature(alert.getSensorData().getTemperature())
                .humidity(alert.getSensorData().getHumidity())
                .timestamp(alert.getSensorData().getTimestamp())
                .coolingFanOn(fanOn)
                .heaterOn(htrOn)
                .humidifierOn(humiOn)
                .build();

        sseEmitterService.sendToDevice(alert.getDeviceId(), payload);
    }
}
