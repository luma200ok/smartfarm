package com.smartfarm.server.sensor.service;

import com.smartfarm.server.sensor.entity.SensorData;
import com.smartfarm.server.device.dto.DeviceConfigView;
import lombok.Builder;
import lombok.Getter;

/**
 * 센서 데이터 기반 경고 상태를 나타내는 도메인 객체
 * - 임계값 초과 여부
 * - 제어 명령 (쿨링팬, 히터)
 * - 경고 메시지
 */
@Getter
@Builder
public class SensorAlert {

    private final String deviceId;
    private final SensorData sensorData;
    private final DeviceConfigView config;

    private final boolean coolingFanOn;
    private final boolean heaterOn;

    private final String coolingMessage;
    private final String heatingMessage;

    /**
     * 경고 발생 여부 판단
     */
    public boolean hasAlert() {
        return coolingFanOn || heaterOn;
    }

    /**
     * 쿨링팬 경고 여부
     */
    public boolean hasCoolingAlert() {
        return coolingFanOn;
    }

    /**
     * 히터 경고 여부
     */
    public boolean hasHeatingAlert() {
        return heaterOn;
    }
}
