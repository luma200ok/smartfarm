package com.smartfarm.server.controller;

import com.smartfarm.server.dto.SensorRequestDto;
import com.smartfarm.server.dto.SensorResponseDto;
import com.smartfarm.server.service.SensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sensor")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;

    /**
     * 센서 데이터 수신 엔드포인트 (역제어 명령 반환)
     * @Valid 어노테이션으로 DTO 유효성 검사 수행 (3번 작업)
     */
    @PostMapping("/data")
    public ResponseEntity<SensorResponseDto> receiveSensorData(@Valid @RequestBody SensorRequestDto requestDto) {
        // 서비스에서 데이터를 처리하고 반환한 결과(제어 명령)를 다시 클라이언트(PC)로 내려보냅니다.
        SensorResponseDto responseDto = sensorService.processSensorData(requestDto);
        return ResponseEntity.ok(responseDto);
    }
}
