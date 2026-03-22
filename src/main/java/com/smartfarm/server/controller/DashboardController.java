package com.smartfarm.server.controller;

import com.smartfarm.server.dto.DailyStatisticsDto;
import com.smartfarm.server.dto.DeviceComparisonDto;
import com.smartfarm.server.dto.SensorHistoryResponseDto;
import com.smartfarm.server.dto.SensorStatisticsDto;
import com.smartfarm.server.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "2. 대시보드 통계 API",
        description = "프론트엔드 대시보드 화면 구성을 위한 데이터 조회 및 통계 API")
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "특정 기기 이력 페이징 조회",
            description = "MySQL에 1분 단위로 저장된 기기의 과거 데이터를 최신순으로 페이징하여 가져옵니다.")
    @GetMapping("/history")
    public ResponseEntity<Page<SensorHistoryResponseDto>> getSensorHistory(
            @Parameter(description = "조회할 기기의 고유 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId,
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 보여줄 데이터 수", example = "10") @RequestParam(defaultValue = "10") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        return ResponseEntity.ok(dashboardService.getSensorHistory(deviceId, pageRequest));
    }

    @Operation(summary = "오늘 하루 통계 데이터 조회 (Querydsl)",
            description = "오늘 자정부터 현재까지 특정 기기의 최고/최저/평균 온도 및 습도를 반환합니다.")
    @GetMapping("/statistics/today")
    public ResponseEntity<SensorStatisticsDto> getTodayStatistics(
            @Parameter(description = "조회할 기기의 고유 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId) {

        SensorStatisticsDto statistics = dashboardService.getTodayStatistics(deviceId);
        if (statistics == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(statistics);
    }

    @Operation(summary = "일별 트렌드 통계 조회",
            description = "최근 N일간의 일별 평균/최고/최저 온도 및 평균 습도를 반환합니다.")
    @GetMapping("/statistics/trend")
    public ResponseEntity<List<DailyStatisticsDto>> getDailyTrend(
            @Parameter(description = "조회할 기기의 고유 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId,
            @Parameter(description = "조회할 기간 (일 수, 기본 7일)", example = "7") @RequestParam(defaultValue = "7") int days) {

        return ResponseEntity.ok(dashboardService.getDailyTrend(deviceId, days));
    }

    @Operation(summary = "알림 발생 횟수 조회",
            description = "최근 N일간 임계값 초과로 인한 알림 발생 횟수를 반환합니다.")
    @GetMapping("/statistics/alert-count")
    public ResponseEntity<Map<String, Long>> getAlertCount(
            @Parameter(description = "조회할 기기의 고유 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId,
            @Parameter(description = "조회할 기간 (일 수, 기본 30일)", example = "30") @RequestParam(defaultValue = "30") int days) {

        long count = dashboardService.getAlertCount(deviceId, days);
        return ResponseEntity.ok(Map.of("count", count, "days", (long) days));
    }

    @Operation(summary = "전체 기기 비교 통계 조회",
            description = "등록된 모든 기기의 오늘 통계(최고/최저/평균 온도, 평균 습도, 임계값)를 비교하여 반환합니다.")
    @GetMapping("/statistics/comparison")
    public ResponseEntity<List<DeviceComparisonDto>> getAllDevicesComparison() {
        return ResponseEntity.ok(dashboardService.getAllDevicesComparison());
    }
}
