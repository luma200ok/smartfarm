package com.smartfarm.server.sensor.controller;

import com.smartfarm.server.sensor.dto.SensorRequestDto;
import com.smartfarm.server.sensor.dto.SensorResponseDto;
import com.smartfarm.server.sensor.service.SensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "1. 센서 제어 API",
        description = "기기로부터 데이터를 수신하고 역제어 명령을 내리는 핵심 API")
@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    @Operation(summary = "센서 데이터 수신 및 명령 반환",
            description = "기기(PC)가 3초마다 보내는 온도/습도 데이터를 수신하고, 임계치 초과 시 쿨링팬/히터 가동 명령을 응답으로 반환합니다.")
    @PostMapping("/data")
    public ResponseEntity<SensorResponseDto> receiveSensorData(@Valid @RequestBody SensorRequestDto requestDto) {
        SensorResponseDto responseDto = sensorService.processSensorData(requestDto);
        return ResponseEntity.ok(responseDto);
    }
}