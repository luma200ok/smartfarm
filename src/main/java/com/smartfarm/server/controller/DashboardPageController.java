package com.smartfarm.server.controller;

import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.service.DashboardService;
import com.smartfarm.server.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardPageController {

    private final DashboardService dashboardService;
    private final ExportService exportService;

    /**
     * 대시보드 메인 페이지 반환
     */
    @GetMapping
    public String dashboard() {
        return "dashboard";
    }

    /**
     * 실제 데이터를 전송한 기기 ID 목록 조회 (대시보드 셀렉터용)
     * SensorHistory 기준 - 데이터를 한 번이라도 보낸 PC만 자동으로 등록됨
     */
    @GetMapping("/devices")
    @ResponseBody
    public ResponseEntity<List<String>> getDeviceIds() {
        return ResponseEntity.ok(dashboardService.getDeviceIds());
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

    @GetMapping("/export/csv")
    public void exportCsv(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletResponse response) throws IOException {
        exportService.exportCsv(deviceId, start, end, response);
    }

    @GetMapping("/export/excel")
    public void exportExcel(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletResponse response) throws IOException {
        exportService.exportExcel(deviceId, start, end, response);
    }
}
