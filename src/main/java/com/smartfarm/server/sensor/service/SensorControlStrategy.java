package com.smartfarm.server.sensor.service;

import com.smartfarm.server.sensor.entity.SensorData;
import com.smartfarm.server.device.dto.DeviceConfigView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 센서 데이터와 기기 설정을 기반으로 제어 판단하는 비즈니스 로직
 * - 온도 임계값: 상한 이상 → 쿨링팬 ON, 하한 이하 → 쿨링팬 OFF
 * - 온도 중간값: 하한~중간 구간 도달 → 쿨링팬 중간값 OFF
 * - 습도 임계값: 하한 이하 → 가습기 ON (건조), 상한 이상 → 가습기 OFF
 * - 습도 중간값: 중간~상한 구간 도달 → 가습기 중간값 OFF
 */
@Slf4j
@Component
public class SensorControlStrategy {

    /**
     * @param currentState DeviceControlService.getDeviceState() 결과 — midOff 조건 판단에 사용
     */
    public SensorAlert determineControl(SensorData sensorData, DeviceConfigView config, Map<String, Boolean> currentState) {
        double temp     = sensorData.getTemperature();
        double humidity = sensorData.getHumidity();

        boolean coolingFanOn  = temp >= config.temperatureThresholdHigh();
        boolean coolingFanOff = temp <= config.temperatureThresholdLow();
        boolean humidifierOn  = humidity <= config.humidityThresholdLow();   // 건조할 때 ON
        boolean humidifierOff = humidity >= config.humidityThresholdHigh();  // 충분히 가습됐을 때 OFF

        // 중간값 도달 시 자동 OFF (기기가 실제 ON 상태일 때만 동작)
        boolean fanCurrentlyOn  = currentState.getOrDefault("coolingFanOn", false);
        boolean humiCurrentlyOn = currentState.getOrDefault("humidifierOn", false);
        double tempMid     = (config.temperatureThresholdLow() + config.temperatureThresholdHigh()) / 2.0;
        double humidityMid = (config.humidityThresholdLow() + config.humidityThresholdHigh()) / 2.0;
        boolean coolingFanMidOff = fanCurrentlyOn  && !coolingFanOn && !coolingFanOff && temp <= tempMid;
        boolean humidifierMidOff = humiCurrentlyOn && !humidifierOn && !humidifierOff && humidity >= humidityMid;

        String coolingMessage    = null;
        String humidifierMessage = null;

        if (coolingFanOn) {
            coolingMessage = String.format("현재 온도: %.1f°C, 상한 기준: %.1f°C", temp, config.temperatureThresholdHigh());
            log.warn("🚨 {} 온도 경고! 쿨링팬 ON ({})", sensorData.getDeviceId(), coolingMessage);
        } else if (coolingFanOff) {
            coolingMessage = String.format("현재 온도: %.1f°C, 하한 기준: %.1f°C", temp, config.temperatureThresholdLow());
            log.info("✅ {} 온도 하한 도달. 쿨링팬 OFF ({})", sensorData.getDeviceId(), coolingMessage);
        } else if (coolingFanMidOff) {
            coolingMessage = String.format("현재 온도: %.1f°C, 중간값: %.1f°C", temp, tempMid);
            log.info("🌡 {} 온도 중간값 도달. 쿨링팬 자동 OFF ({})", sensorData.getDeviceId(), coolingMessage);
        }

        if (humidifierOn) {
            humidifierMessage = String.format("현재 습도: %.1f%%, 하한 기준: %.1f%%", humidity, config.humidityThresholdLow());
            log.warn("💧 {} 습도 낮음! 가습기 ON ({})", sensorData.getDeviceId(), humidifierMessage);
        } else if (humidifierOff) {
            humidifierMessage = String.format("현재 습도: %.1f%%, 상한 기준: %.1f%%", humidity, config.humidityThresholdHigh());
            log.info("✅ {} 습도 충분. 가습기 OFF ({})", sensorData.getDeviceId(), humidifierMessage);
        } else if (humidifierMidOff) {
            humidifierMessage = String.format("현재 습도: %.1f%%, 중간값: %.1f%%", humidity, humidityMid);
            log.info("💦 {} 습도 중간값 도달. 가습기 자동 OFF ({})", sensorData.getDeviceId(), humidifierMessage);
        }

        return SensorAlert.builder()
                .deviceId(sensorData.getDeviceId())
                .sensorData(sensorData)
                .config(config)
                .coolingFanOn(coolingFanOn)
                .coolingFanOff(coolingFanOff)
                .humidifierOn(humidifierOn)
                .humidifierOff(humidifierOff)
                .coolingFanMidOff(coolingFanMidOff)
                .humidifierMidOff(humidifierMidOff)
                .coolingMessage(coolingMessage)
                .humidifierMessage(humidifierMessage)
                .build();
    }
}
