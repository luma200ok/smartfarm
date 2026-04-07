package com.smartfarm.server.dashboard.service;

import com.smartfarm.server.sensor.dto.DailyStatisticsDto;
import com.smartfarm.server.device.dto.DeviceComparisonDto;
import com.smartfarm.server.sensor.dto.SensorHistoryResponseDto;
import com.smartfarm.server.sensor.dto.SensorStatisticsDto;
import com.smartfarm.server.control.entity.ControlEventLog;
import com.smartfarm.server.device.entity.DeviceConfig;
import com.smartfarm.server.control.repository.ControlEventLogRepository;
import com.smartfarm.server.device.repository.DeviceConfigRepository;
import com.smartfarm.server.sensor.repository.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    @Value("${smartfarm.sensor.default-temp-threshold-high}")
    private double defaultTempThresholdHigh;

    @Value("${smartfarm.sensor.default-humidity-threshold-high}")
    private double defaultHumidityThresholdHigh;

    public List<String> getDeviceIds() {
        return deviceConfigRepository.findAllDeviceIds();
    }

    public Page<SensorHistoryResponseDto> getSensorHistory(String deviceId, Pageable pageable) {
        return historyRepository.findByDeviceIdAndDeletedAtIsNullOrderByTimestampDesc(deviceId, pageable)
                .map(SensorHistoryResponseDto::from);
    }

    public Page<ControlEventLog> getControlEventLogs(String deviceId, Pageable pageable) {
        return eventLogRepository.findByDeviceIdOrderByTimestampDesc(deviceId, pageable);
    }

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public SensorStatisticsDto getTodayStatistics(String deviceId) {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.atTime(23, 59, 59, 999999999);
        return historyRepository.getSensorStatistics(deviceId, startOfDay, endOfDay);
    }

    public List<DailyStatisticsDto> getDailyTrend(String deviceId, int days) {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime start = today.minusDays(days - 1L).atStartOfDay();
        LocalDateTime end   = today.atTime(23, 59, 59, 999999999);
        return historyRepository.getDailyStatistics(deviceId, start, end);
    }

    public long getAlertCount(String deviceId, int days) {
        LocalDateTime start = LocalDateTime.now(KST).minusDays(days);
        LocalDateTime end   = LocalDateTime.now(KST);
        return eventLogRepository.countByDeviceIdAndTimestampBetween(deviceId, start, end);
    }

    public List<DeviceComparisonDto> getAllDevicesComparison() {
        LocalDate today = LocalDate.now(KST);
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime endOfDay   = today.atTime(23, 59, 59, 999999999);

        List<DeviceConfig> configs = deviceConfigRepository.findAll();
        if (configs.isEmpty()) {
            return List.of();
        }

        List<String> deviceIds = configs.stream()
                .map(DeviceConfig::getDeviceId)
                .collect(Collectors.toList());

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
                            .tempThresholdHigh(config.getTemperatureThresholdHigh() != null
                                    ? config.getTemperatureThresholdHigh() : defaultTempThresholdHigh)
                            .humidityThresholdHigh(config.getHumidityThresholdHigh() != null
                                    ? config.getHumidityThresholdHigh() : defaultHumidityThresholdHigh)
                            .build();
                })
                .collect(Collectors.toList());
    }
}
