package com.smartfarm.server.controller;

import com.smartfarm.server.dto.DeviceConfigRequestDto;
import com.smartfarm.server.dto.DeviceConfigResponseDto;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.security.UserPrincipal;
import com.smartfarm.server.service.DeviceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-config")
@RequiredArgsConstructor
@Tag(name = "3. 기기 설정 API", description = "기기별 온도/메모리 임계값 관리")
public class DeviceConfigController {

    private final DeviceConfigService deviceConfigService;

    @Value("${smartfarm.sensor.default-temp-threshold}")
    private double defaultTempThreshold;

    @Value("${smartfarm.sensor.default-mem-usage-threshold}")
    private double defaultMemUsageThreshold;

    @Operation(summary = "전체 기기 설정 조회 (admin만 전체, 일반 사용자는 자기 기기만)")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DeviceConfigResponseDto>> getAllConfigs(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal.isAdmin()) {
            return ResponseEntity.ok(deviceConfigService.getAllDeviceConfigs());
        }
        // 일반 사용자 — 자신의 기기 설정만 반환
        String linked = principal.getLinkedDeviceId();
        if (linked == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(List.of(
                DeviceConfigResponseDto.from(deviceConfigService.getDeviceConfig(linked),
                        defaultTempThreshold, defaultMemUsageThreshold)));
    }

    @Operation(summary = "특정 기기 설정 조회")
    @GetMapping("/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeviceConfigResponseDto> getConfig(
            @PathVariable String deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, deviceId);
        return ResponseEntity.ok(
                DeviceConfigResponseDto.from(deviceConfigService.getDeviceConfig(deviceId),
                        defaultTempThreshold, defaultMemUsageThreshold));
    }

    @Operation(summary = "기기 설정 저장/수정")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeviceConfigResponseDto> saveOrUpdate(
            @Valid @RequestBody DeviceConfigRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, request.getDeviceId());
        return ResponseEntity.ok(deviceConfigService.saveOrUpdateDeviceConfig(request));
    }

    @Operation(summary = "기기 설정 삭제")
    @DeleteMapping("/{deviceId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteConfig(@PathVariable String deviceId) {
        deviceConfigService.deleteDeviceConfig(deviceId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "API 키 재발급", description = "기기의 API 키를 새 UUID로 교체합니다. 기존 키는 즉시 무효화됩니다.")
    @PostMapping("/{deviceId}/regenerate-key")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceConfigResponseDto> regenerateApiKey(@PathVariable String deviceId) {
        return ResponseEntity.ok(deviceConfigService.regenerateApiKey(deviceId));
    }

    private void assertDeviceAccess(UserPrincipal principal, String deviceId) {
        if (!principal.canAccess(deviceId)) {
            throw new CustomException(ErrorCode.DEVICE_ACCESS_DENIED);
        }
    }
}
