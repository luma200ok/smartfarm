package com.smartfarm.server.controller;

import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardPageController {

    private final DashboardService dashboardService;

    /**
     * 대시보드 메인 페이지 반환
     */
    @GetMapping
    public String dashboard() {
        return "dashboard";
    }

    /**
     * 제어 이벤트 로그 조회 API (대시보드 전용)
     * 기존 /api/dashboard/* REST API와 분리된 뷰 전용 엔드포인트
     */
    @GetMapping("/events")
    @ResponseBody
    public ResponseEntity<Page<ControlEventLog>> getControlEventLogs(
            @RequestParam String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<ControlEventLog> result = dashboardService.getControlEventLogs(deviceId, pageRequest);
        return ResponseEntity.ok(result);
    }
}
