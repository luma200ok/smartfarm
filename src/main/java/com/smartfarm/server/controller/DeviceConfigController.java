package com.smartfarm.server.controller;

import com.smartfarm.server.dto.DeviceConfigRequestDto;
import com.smartfarm.server.dto.DeviceConfigResponseDto;
import com.smartfarm.server.service.DeviceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-config")
@RequiredArgsConstructor
@Tag(name = "3. 기기 설정 API", description = "기기별 온도/습도 임계값 관리")
public class DeviceConfigController {

    private final DeviceConfigService deviceConfigService;

    @Operation(summary = "전체 기기 설정 조회")
    @GetMapping
    public ResponseEntity<List<DeviceConfigResponseDto>> getAllConfigs() {
        return ResponseEntity.ok(deviceConfigService.getAllDeviceConfigs());
    }

    @Operation(summary = "특정 기기 설정 조회")
    @GetMapping("/{deviceId}")
    public ResponseEntity<DeviceConfigResponseDto> getConfig(@PathVariable String deviceId) {
        return ResponseEntity.ok(DeviceConfigResponseDto.from(deviceConfigService.getDeviceConfig(deviceId)));
    }

    @Operation(summary = "기기 설정 저장/수정")
    @PostMapping
    public ResponseEntity<DeviceConfigResponseDto> saveOrUpdate(@Valid @RequestBody DeviceConfigRequestDto request) {
        return ResponseEntity.ok(DeviceConfigResponseDto.from(deviceConfigService.saveOrUpdateDeviceConfig(request)));
    }

    @Operation(summary = "기기 설정 삭제")
    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> deleteConfig(@PathVariable String deviceId) {
        deviceConfigService.deleteDeviceConfig(deviceId);
        return ResponseEntity.noContent().build();
    }
}
