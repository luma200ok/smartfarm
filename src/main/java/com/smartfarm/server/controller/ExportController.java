package com.smartfarm.server.controller;

import com.smartfarm.server.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.LocalDateTime;

@Tag(name = "6. 데이터 내보내기 API", description = "센서 이력 데이터를 CSV 또는 Excel로 내보냅니다.")
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @Operation(
            summary = "CSV 내보내기",
            description = "기간 내 센서 이력 데이터를 UTF-8 BOM CSV 파일로 다운로드합니다. (ADMIN만 가능)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/csv")
    public void exportCsv(
            @Parameter(description = "기기 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId,
            @Parameter(description = "시작 일시 (ISO 형식)", example = "2025-01-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "종료 일시 (ISO 형식)", example = "2025-01-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletResponse response) throws IOException {
        exportService.exportCsv(deviceId, start, end, response);
    }

    @Operation(
            summary = "Excel 내보내기",
            description = "기간 내 센서 이력 데이터를 xlsx 파일로 다운로드합니다. (ADMIN만 가능)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/excel")
    public void exportExcel(
            @Parameter(description = "기기 ID", example = "WINDOWS_PC_01") @RequestParam String deviceId,
            @Parameter(description = "시작 일시 (ISO 형식)", example = "2025-01-01T00:00:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @Parameter(description = "종료 일시 (ISO 형식)", example = "2025-01-31T23:59:59")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            HttpServletResponse response) throws IOException {
        exportService.exportExcel(deviceId, start, end, response);
    }
}
