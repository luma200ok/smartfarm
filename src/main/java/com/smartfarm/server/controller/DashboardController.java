package com.smartfarm.server.controller;

import com.smartfarm.server.dto.SensorHistoryResponseDto;
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
     * 대시보드용 특정 기기 이력 조회 API
     * URL 예시: /api/dashboard/history?deviceId=WINDOWS_PC_01&page=0&size=10
     */
    @GetMapping("/history")
    public ResponseEntity<Page<SensorHistoryResponseDto>> getSensorHistory(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        // PageRequest를 생성하여 페이징 정보를 서비스에 전달합니다.
        PageRequest pageRequest = PageRequest.of(page, size);
        Page<SensorHistoryResponseDto> result = dashboardService.getSensorHistory(deviceId, pageRequest);
        
        return ResponseEntity.ok(result);
    }
}
