package com.smartfarm.server.service;

import com.smartfarm.server.dto.DailyStatisticsDto;
import com.smartfarm.server.dto.DeviceComparisonDto;
import com.smartfarm.server.dto.SensorHistoryResponseDto;
import com.smartfarm.server.dto.SensorStatisticsDto;
import com.smartfarm.server.entity.ControlEventLog;
import com.smartfarm.server.entity.DeviceConfig;
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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = LocalDate.now().atTime(23, 59, 59, 999999999);
        return historyRepository.getSensorStatistics(deviceId, startOfDay, endOfDay);
    }

    /**
     * 특정 기기의 최근 N일 일별 트렌드 통계를 조회합니다.
     */
    public List<DailyStatisticsDto> getDailyTrend(String deviceId, int days) {
        LocalDateTime start = LocalDate.now().minusDays(days - 1L).atStartOfDay();
        LocalDateTime end   = LocalDate.now().atTime(23, 59, 59, 999999999);
        return historyRepository.getDailyStatistics(deviceId, start, end);
    }

    /**
     * 특정 기기의 최근 N일간 알림(임계값 초과) 발생 횟수를 조회합니다.
     */
    public long getAlertCount(String deviceId, int days) {
        LocalDateTime start = LocalDateTime.now().minusDays(days);
        LocalDateTime end   = LocalDateTime.now();
        return eventLogRepository.countByDeviceIdAndTimestampBetween(deviceId, start, end);
    }

    /**
     * 등록된 모든 기기의 오늘 통계를 비교하여 반환합니다.
     * 단일 쿼리로 모든 기기 통계를 조회하여 N+1 문제를 방지합니다.
     */
    public List<DeviceComparisonDto> getAllDevicesComparison() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay   = LocalDate.now().atTime(23, 59, 59, 999999999);

        List<DeviceConfig> configs = deviceConfigRepository.findAll();
        if (configs.isEmpty()) {
            return List.of();
        }

        List<String> deviceIds = configs.stream()
                .map(DeviceConfig::getDeviceId)
                .collect(Collectors.toList());

        // 단일 쿼리로 모든 기기 통계 한 번에 조회 (N+1 방지)
        Map<String, SensorStatisticsDto> statsMap =
                historyRepository.getAllDevicesStatistics(deviceIds, startOfDay, endOfDay);

        return configs.stream()
                .map(config -> {
                    SensorStatisticsDto stats = statsMap.get(config.getDeviceId());
                    return DeviceComparisonDto.builder()
                            .deviceId(config.getDeviceId())
                            .maxTemperature(stats != null ? stats.getMaxTemperature() : null)
                            .minTemperature(stats != null ? stats.getMinTemperature() : null)
                            .avgTemperature(stats != null ? stats.getAvgTemperature() : null)
                            .avgHumidity(stats != null ? stats.getAvgHumidity() : null)
                            .tempThreshold(config.getTemperatureThresholdHigh())
                            .humidityThreshold(config.getHumidityThresholdHigh())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
