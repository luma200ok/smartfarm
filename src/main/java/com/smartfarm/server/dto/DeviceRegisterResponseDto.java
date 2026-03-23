package com.smartfarm.server.dto;

import com.smartfarm.server.entity.DeviceConfig;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DeviceRegisterResponseDto {

    private String deviceId;

    /**
     * 발급된 API 키.
     * 이 키를 PC 클라이언트의 .env 파일에 저장해 두세요.
     * 재조회가 불가능하므로 분실 시 대시보드에서 재발급이 필요합니다.
     */
    private String apiKey;

    private double temperatureThresholdHigh;
    private double memUsageThresholdHigh;
    private String message;

    public static DeviceRegisterResponseDto from(DeviceConfig config) {
        return DeviceRegisterResponseDto.builder()
                .deviceId(config.getDeviceId())
                .apiKey(config.getApiKey())
                .temperatureThresholdHigh(config.getTemperatureThresholdHigh())
                .memUsageThresholdHigh(config.getMemUsageThresholdHigh())
                .message("기기 등록 완료. API 키를 .env 파일에 저장하세요. 재조회 불가 — 분실 시 대시보드에서 재발급하세요.")
                .build();
    }
}
