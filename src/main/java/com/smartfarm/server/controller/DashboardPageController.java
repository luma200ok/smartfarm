package com.smartfarm.server.controller;

import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.security.UserPrincipal;
import com.smartfarm.server.service.DashboardService;
import com.smartfarm.server.service.ExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final ExportService    exportService;

    @GetMapping
    public String dashboard() {
        return "dashboard";
    }

    /**
     * 기기 ID 목록 조회.
     * admin → 전체, 일반 사용자 → 자신의 기기만
     */
    @GetMapping("/devices")
    @ResponseBody
    public ResponseEntity<List<String>> getDeviceIds(
            @AuthenticationPrincipal UserPrincipal principal) {

        if (principal.isAdmin()) {
            return ResponseEntity.ok(dashboardService.getDeviceIds());
        }
        // 일반 사용자 — linkedDeviceId가 설정된 경우만 반환
        String linked = principal.getLinkedDeviceId();
        return ResponseEntity.ok(linked != null ? List.of(linked) : List.of());
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
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {

        assertDeviceAccess(principal, deviceId);
        return ResponseEntity.ok(
                dashboardService.getControlEventLogs(deviceId, PageRequest.of(page, size)));
    }

    @GetMapping("/export/csv")
    public void exportCsv(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletResponse response,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        assertDeviceAccess(principal, deviceId);
        exportService.exportCsv(deviceId, start, end, response);
    }

    @GetMapping("/export/excel")
    public void exportExcel(
            @RequestParam String deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletResponse response,
            @AuthenticationPrincipal UserPrincipal principal) throws IOException {

        assertDeviceAccess(principal, deviceId);
        exportService.exportExcel(deviceId, start, end, response);
    }

    /** deviceId 접근 권한 검사 공통 메서드 */
    private void assertDeviceAccess(UserPrincipal principal, String deviceId) {
        if (!principal.canAccess(deviceId)) {
            throw new CustomException(ErrorCode.DEVICE_ACCESS_DENIED);
        }
    }
}
