package com.smartfarm.server.sensor.service;

import com.smartfarm.server.sensor.entity.SensorData;
import com.smartfarm.server.device.dto.DeviceConfigView;
import lombok.Builder;
import lombok.Getter;

/**
 * 센서 데이터 기반 경고 상태를 나타내는 도메인 객체
 * - 임계값 초과 여부
 * - 제어 명령 (쿨링팬, 가습기)
 * - 경고 메시지
 */
@Getter
@Builder
public class SensorAlert {

    private final String deviceId;
    private final SensorData sensorData;
    private final DeviceConfigView config;

    private final boolean coolingFanOn;   // 온도 >= 상한 → 쿨링팬 ON
    private final boolean coolingFanOff;  // 온도 <= 하한 → 쿨링팬 OFF
    private final boolean humidifierOn;   // 습도 >= 상한 → 가습기 ON
    private final boolean humidifierOff;  // 습도 <= 하한 → 가습기 OFF

    private final String coolingMessage;
    private final String humidifierMessage;

    public boolean hasAlert() {
        return coolingFanOn || coolingFanOff || humidifierOn || humidifierOff;
    }

    public boolean hasCoolingAlert() {
        return coolingFanOn || coolingFanOff;
    }

    public boolean hasHumidifierAlert() {
        return humidifierOn || humidifierOff;
    }
}
