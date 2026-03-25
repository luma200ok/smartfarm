package com.smartfarm.server.service.strategy;

import com.smartfarm.server.entity.DeviceConfig;
import com.smartfarm.server.entity.SensorData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 센서 데이터와 기기 설정을 기반으로 제어 판단하는 비즈니스 로직
 * - 온도/습도 임계값 비교
 * - 쿨링팬/히터 제어 결정
 */
@Slf4j
@Component
public class SensorControlStrategy {

    /**
     * 센서 데이터와 기기 설정을 비교하여 제어 여부 결정
     * @param sensorData 수신된 센서 데이터
     * @param config 기기 설정 (임계값)
     * @return 제어 상태 정보
     */
    public SensorAlert determineControl(SensorData sensorData, DeviceConfig config) {
        boolean needCooling = sensorData.getTemperature() >= config.getTemperatureThresholdHigh();
        boolean needHeater = sensorData.getHumidity() >= config.getHumidityThresholdHigh();

        String coolingMessage = null;
        String heatingMessage = null;

        if (needCooling) {
            coolingMessage = String.format("현재 온도: %.1f도, 설정 기준치: %.1f도",
                    sensorData.getTemperature(), config.getTemperatureThresholdHigh());
            log.warn("🚨 {} 온도 경고! 쿨링팬 가동 명령 발행! ({})", sensorData.getDeviceId(), coolingMessage);
        }

        if (needHeater) {
            heatingMessage = String.format("현재 습도: %.1f%%, 설정 기준치: %.1f%%",
                    sensorData.getHumidity(), config.getHumidityThresholdHigh());
            log.warn("🚨 {} 습도 경고! 히터 가동 명령 발행! ({})", sensorData.getDeviceId(), heatingMessage);
        }

        return SensorAlert.builder()
                .deviceId(sensorData.getDeviceId())
                .sensorData(sensorData)
                .coolingFanOn(needCooling)
                .heaterOn(needHeater)
                .coolingMessage(coolingMessage)
                .heatingMessage(heatingMessage)
                .build();
    }
}
