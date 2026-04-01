package com.smartfarm.server.sensor.service;

import com.smartfarm.server.sensor.entity.SensorData;
import com.smartfarm.server.device.dto.DeviceConfigView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 센서 데이터와 기기 설정을 기반으로 제어 판단하는 비즈니스 로직
 * - 온도 임계값: 26°C 이상 → 쿨링팬 ON, 20°C 이하 → 쿨링팬 OFF
 * - 습도 임계값: 70% 이상 → 가습기 ON, 40% 이하 → 가습기 OFF
 */
@Slf4j
@Component
public class SensorControlStrategy {

    public SensorAlert determineControl(SensorData sensorData, DeviceConfigView config) {
        double temp     = sensorData.getTemperature();
        double humidity = sensorData.getHumidity();

        boolean coolingFanOn  = temp >= config.temperatureThresholdHigh();
        boolean coolingFanOff = temp <= config.temperatureThresholdLow();
        boolean humidifierOn  = humidity >= config.humidityThresholdHigh();
        boolean humidifierOff = humidity <= config.humidityThresholdLow();

        String coolingMessage    = null;
        String humidifierMessage = null;

        if (coolingFanOn) {
            coolingMessage = String.format("현재 온도: %.1f°C, 상한 기준: %.1f°C", temp, config.temperatureThresholdHigh());
            log.warn("🚨 {} 온도 경고! 쿨링팬 ON ({})", sensorData.getDeviceId(), coolingMessage);
        } else if (coolingFanOff) {
            coolingMessage = String.format("현재 온도: %.1f°C, 하한 기준: %.1f°C", temp, config.temperatureThresholdLow());
            log.info("✅ {} 온도 정상. 쿨링팬 OFF ({})", sensorData.getDeviceId(), coolingMessage);
        }

        if (humidifierOn) {
            humidifierMessage = String.format("현재 습도: %.1f%%, 상한 기준: %.1f%%", humidity, config.humidityThresholdHigh());
            log.warn("💧 {} 습도 경고! 가습기 ON ({})", sensorData.getDeviceId(), humidifierMessage);
        } else if (humidifierOff) {
            humidifierMessage = String.format("현재 습도: %.1f%%, 하한 기준: %.1f%%", humidity, config.humidityThresholdLow());
            log.info("✅ {} 습도 정상. 가습기 OFF ({})", sensorData.getDeviceId(), humidifierMessage);
        }

        return SensorAlert.builder()
                .deviceId(sensorData.getDeviceId())
                .sensorData(sensorData)
                .config(config)
                .coolingFanOn(coolingFanOn)
                .coolingFanOff(coolingFanOff)
                .humidifierOn(humidifierOn)
                .humidifierOff(humidifierOff)
                .coolingMessage(coolingMessage)
                .humidifierMessage(humidifierMessage)
                .build();
    }
}
