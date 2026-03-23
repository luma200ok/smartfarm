package com.smartfarm.server.dashboard.controller;

import com.smartfarm.server.sensor.dto.DailyStatisticsDto;
import com.smartfarm.server.device.dto.DeviceComparisonDto;
import com.smartfarm.server.sensor.dto.SensorHistoryResponseDto;
import com.smartfarm.server.sensor.dto.SensorStatisticsDto;
import com.smartfarm.server.common.exception.CustomException;
import com.smartfarm.server.common.exception.ErrorCode;
import com.smartfarm.server.common.security.UserPrincipal;
import com.smartfarm.server.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "2. 대시보드 통계 API", description = "프론트엔드 대시보드 화면 구성을 위한 데이터 조회 및 통계 API")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "특정 기기 이력 페이징 조회")
    @GetMapping("/history")
    public ResponseEntity<Page<SensorHistoryResponseDto>> getSensorHistory(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, deviceId);
        return ResponseEntity.ok(dashboardService.getSensorHistory(deviceId, PageRequest.of(page, size)));
    }

    @Operation(summary = "오늘 하루 통계 데이터 조회")
    @GetMapping("/statistics/today")
    public ResponseEntity<SensorStatisticsDto> getTodayStatistics(
            @RequestParam String deviceId,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, deviceId);
        SensorStatisticsDto statistics = dashboardService.getTodayStatistics(deviceId);
        if (statistics == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(statistics);
    }

    @Operation(summary = "일별 트렌드 통계 조회")
    @GetMapping("/statistics/trend")
    public ResponseEntity<List<DailyStatisticsDto>> getDailyTrend(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, deviceId);
        return ResponseEntity.ok(dashboardService.getDailyTrend(deviceId, days));
    }

    @Operation(summary = "알림 발생 횟수 조회")
    @GetMapping("/statistics/alert-count")
    public ResponseEntity<Map<String, Long>> getAlertCount(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, deviceId);
        long count = dashboardService.getAlertCount(deviceId, days);
        return ResponseEntity.ok(Map.of("count", count, "days", (long) days));
    }

    @Operation(summary = "전체 기기 비교 통계 조회")
    @GetMapping("/statistics/comparison")
    public ResponseEntity<List<DeviceComparisonDto>> getAllDevicesComparison(
            @AuthenticationPrincipal UserPrincipal principal) {

        List<DeviceComparisonDto> result = dashboardService.getAllDevicesComparison();

        // 일반 사용자 → 자신의 기기만 필터링
        if (!principal.isAdmin()) {
            String linked = principal.getLinkedDeviceId();
            result = result.stream()
                    .filter(d -> d.getDeviceId().equals(linked))
                    .toList();
        }
        return ResponseEntity.ok(result);
    }

    private void assertDeviceAccess(UserPrincipal principal, String deviceId) {
        if (!principal.canAccess(deviceId)) {
            throw new CustomException(ErrorCode.DEVICE_ACCESS_DENIED);
        }
    }
}
