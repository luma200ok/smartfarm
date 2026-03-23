package com.smartfarm.server.service;

import com.smartfarm.server.entity.SensorHistory;
import com.smartfarm.server.repository.SensorHistoryRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final SensorHistoryRepository historyRepository;

    public void exportCsv(String deviceId, LocalDateTime start, LocalDateTime end,
                          HttpServletResponse response) throws IOException {
        List<SensorHistory> rows = historyRepository
                .findByDeviceIdAndTimestampBetweenAndDeletedAtIsNullOrderByTimestampAsc(deviceId, start, end);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + deviceId + "_export.csv\"");
        // UTF-8 BOM: Excel에서 한글 깨짐 방지
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        PrintWriter writer = response.getWriter();
        writer.println("ID,기기ID,온도(°C),메모리사용률(%),측정시각");
        for (SensorHistory h : rows) {
            writer.printf("%d,%s,%.1f,%.1f,%s%n",
                    h.getId(), h.getDeviceId(),
                    h.getTemperature(), h.getMemUsage(),
                    h.getTimestamp().toString());
        }
    }

    public void exportExcel(String deviceId, LocalDateTime start, LocalDateTime end,
                            HttpServletResponse response) throws IOException {
        List<SensorHistory> rows = historyRepository
                .findByDeviceIdAndTimestampBetweenAndDeletedAtIsNullOrderByTimestampAsc(deviceId, start, end);

        response.setContentType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + deviceId + "_export.xlsx\"");

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("센서 이력");

            Row header = sheet.createRow(0);
            String[] cols = {"ID", "기기ID", "온도(°C)", "메모리사용률(%)", "측정시각"};
            for (int i = 0; i < cols.length; i++) {
                header.createCell(i).setCellValue(cols[i]);
            }

            int rowNum = 1;
            for (SensorHistory h : rows) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(h.getId());
                row.createCell(1).setCellValue(h.getDeviceId());
                row.createCell(2).setCellValue(h.getTemperature());
                row.createCell(3).setCellValue(h.getMemUsage());
                row.createCell(4).setCellValue(h.getTimestamp().toString());
            }

            wb.write(response.getOutputStream());
        }
    }
}
