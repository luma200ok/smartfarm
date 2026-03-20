package com.smartfarm.server.controller;

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
        Page<SensorHistoryResponseDto> result = dashboardService.getSensorHistory(deviceId, pageRequest);
        
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "오늘 하루 통계 데이터 조회 (Querydsl)",
            description = "오늘 자정부터 현재까지 특정 기기에서 발생한 최고/최저/평균 온도 및 습도를 동적으로 계산하여 반환합니다.")
    @GetMapping("/statistics/today")
    public ResponseEntity<SensorStatisticsDto> getTodayStatistics(
            @Parameter(description = "조회할 기기의 고유 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId) {
        
        SensorStatisticsDto statistics = dashboardService.getTodayStatistics(deviceId);
        
        if (statistics == null) {
            return ResponseEntity.noContent().build();
        }
        
        return ResponseEntity.ok(statistics);
    }
}