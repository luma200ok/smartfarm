package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorHistoryResponseDto;
import com.smartfarm.server.dto.SensorStatisticsDto;
import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.repository.ControlEventLogRepository;
import com.smartfarm.server.repository.DeviceConfigRepository;
import com.smartfarm.server.repository.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 데이터 변경(CUD) 없이 조회(Read)만 하므로 성능 최적화를 위해 읽기 전용으로 설정합니다.
public class DashboardService {

    private final SensorHistoryRepository historyRepository;
    private final ControlEventLogRepository eventLogRepository;
    private final DeviceConfigRepository deviceConfigRepository;

    /**
     * 등록된 기기 ID 목록을 조회합니다. (대시보드 셀렉터용)
     */
    public List<String> getDeviceIds() {
        return deviceConfigRepository.findAllDeviceIds();
    }

    /**
     * 특정 기기의 최근 이력 데이터를 페이징하여 조회합니다.
     */
    public Page<SensorHistoryResponseDto> getSensorHistory(String deviceId, Pageable pageable) {
        return historyRepository.findByDeviceIdAndDeletedAtIsNullOrderByTimestampDesc(deviceId, pageable)
                .map(SensorHistoryResponseDto::from);
    }

    /**
     * 특정 기기의 제어 이벤트 로그를 페이징하여 조회합니다.
     */
    public Page<ControlEventLog> getControlEventLogs(String deviceId, Pageable pageable) {
        return eventLogRepository.findByDeviceIdOrderByTimestampDesc(deviceId, pageable);
    }

    /**
     * 특정 기기의 오늘 하루 통계(최고, 최저, 평균)를 조회합니다. (Querydsl 사용)
     */
    public SensorStatisticsDto getTodayStatistics(String deviceId) {
        // 오늘 자정 (00:00:00)
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        // 오늘 밤 (23:59:59.999999999)
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59, 999999999);

        // Querydsl로 구현한 커스텀 메서드 호출
        return historyRepository.getSensorStatistics(deviceId, startOfDay, endOfDay);
    }
}
