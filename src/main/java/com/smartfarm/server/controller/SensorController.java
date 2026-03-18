package com.smartfarm.server.controller;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.service.SensorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    /**
     * 센서 데이터 수신 엔드포인트
     */
    @PostMapping("/data")
    public ResponseEntity<String> receiveSensorData(@RequestBody SensorRequestDto requestDto) {
        sensorService.processSensorData(requestDto);
        return ResponseEntity.ok("Data received successfully");
    }
}
