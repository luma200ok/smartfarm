package com.smartfarm.server.controller;

import com.smartfarm.server.dto.SensorHistoryResponseDto;
import com.smartfarm.server.dto.SensorStatisticsDto;
import com.smartfarm.server.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * 대시보드용 특정 기기 최근 이력 페이징 조회 API
     * URL 예시: /api/dashboard/history?deviceId=WINDOWS_PC_01&page=0&size=10
     */
    @GetMapping("/history")
    public ResponseEntity<Page<SensorHistoryResponseDto>> getSensorHistory(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<SensorHistoryResponseDto> result = dashboardService.getSensorHistory(deviceId, pageRequest);
        
        return ResponseEntity.ok(result);
    }

    /**
     * (NEW) 특정 기기의 오늘 하루 통계(최고, 최저, 평균) 조회 API (Querydsl 적용)
     * URL 예시: /api/dashboard/statistics/today?deviceId=WINDOWS_PC_01
     */
    @GetMapping("/statistics/today")
    public ResponseEntity<SensorStatisticsDto> getTodayStatistics(
            @RequestParam String deviceId) {
        
        SensorStatisticsDto statistics = dashboardService.getTodayStatistics(deviceId);
        
        // 데이터가 아예 없는 경우(null) 처리
        if (statistics == null) {
            return ResponseEntity.noContent().build(); // 204 No Content 반환
        }
        
        return ResponseEntity.ok(statistics);
    }
}
