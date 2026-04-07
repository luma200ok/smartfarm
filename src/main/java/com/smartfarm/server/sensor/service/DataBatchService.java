package com.smartfarm.server.sensor.service;

import com.smartfarm.server.sensor.dto.SensorStatisticsDto;
import com.smartfarm.server.sensor.entity.SensorData;
import com.smartfarm.server.sensor.entity.SensorHistory;
import com.smartfarm.server.device.repository.DeviceConfigRepository;
import com.smartfarm.server.sensor.repository.SensorHistoryRepository;
import com.smartfarm.server.sensor.repository.SensorRedisRepository;
import com.smartfarm.server.notification.service.DiscordNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataBatchService {

    private final SensorRedisRepository redisRepository;
    private final SensorHistoryRepository mysqlRepository;
    private final DeviceConfigRepository deviceConfigRepository;
    private final DiscordNotificationService discordNotificationService;

    /**
     * @Scheduled를 사용한 주기적 작업
     * application.yaml의 smartfarm.batch.interval-ms 값을 주기로 사용합니다. (기본 60,000ms = 1분)
     */
    @Transactional // MySQL 저장 중 에러가 발생하면 롤백하고, Redis 데이터 삭제를 방지합니다.
    @Scheduled(fixedDelayString = "${smartfarm.batch.interval-ms}") 
    public void migrateDataFromRedisToMySql() {
        log.info("[BATCH TASK] 주기적 데이터 평균 집계 및 이관 시작 - Redis -> MySQL");
        
        try {
            // 1. Redis에 저장된 모든 센서 데이터를 가져옵니다.
            Iterable<SensorData> redisDataList = redisRepository.findAll();
            
            // Iterable을 List로 변환 (null이거나 deviceId가 없는 비정상 데이터 필터링)
            List<SensorData> validDataList = StreamSupport.stream(redisDataList.spliterator(), false)
                    .filter(data -> data != null && data.getDeviceId() != null)
                    .collect(Collectors.toList());

            log.info("[BATCH TASK] Redis에서 읽은 유효 데이터 수: {}", validDataList.size());
            if (validDataList.isEmpty()) {
                log.warn("[BATCH TASK] 집계할 센서 데이터가 없습니다. Redis가 비어있거나 sensor_agent가 미실행 중일 수 있습니다.");
                return;
            }

            // 2. 디바이스 ID를 기준으로 그룹화 (Grouping)
            Map<String, List<SensorData>> groupedByDevice = validDataList.stream()
                    .collect(Collectors.groupingBy(SensorData::getDeviceId));

            List<SensorHistory> historyListToSave = new ArrayList<>();

            // 3. 각 디바이스별로 평균값(온도, 메모리 사용률)을 계산하여 하나의 History 엔티티로 만듭니다.
            for (Map.Entry<String, List<SensorData>> entry : groupedByDevice.entrySet()) {
                String deviceId = entry.getKey();
                List<SensorData> deviceDataList = entry.getValue();

                double avgTemperature = deviceDataList.stream()
                        .mapToDouble(SensorData::getTemperature)
                        .average()
                        .orElse(0.0);

                double avgHumidity = deviceDataList.stream()
                        .mapToDouble(SensorData::getHumidity)
                        .average()
                        .orElse(0.0);

                // 현재 집계가 끝난 시점의 시간을 KST 기준으로 기록합니다.
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

                // 집계된 '평균값'을 가진 1개의 행(Row) 생성
                SensorHistory averageHistory = SensorHistory.builder()
                        .deviceId(deviceId)
                        .temperature(Math.round(avgTemperature * 10.0) / 10.0) // 소수점 첫째 자리까지만 남김
                        .humidity(Math.round(avgHumidity * 10.0) / 10.0)
                        .timestamp(now)
                        .build();

                historyListToSave.add(averageHistory);
            }

            // 4. MySQL에 집계된 평균 데이터를 저장 (디바이스당 1건씩만 Insert 됨)
            mysqlRepository.saveAll(historyListToSave);
            // flush()로 INSERT SQL을 즉시 실행: Redis 삭제 전에 DB 오류를 확인하기 위함
            // (saveAll은 JPA 1차 캐시에만 큐잉 → 커밋 시점에 실행되면 Redis 삭제 후 실패 가능)
            mysqlRepository.flush();
            log.info("[BATCH TASK] {}개 디바이스의 평균 데이터를 MySQL에 저장했습니다.", historyListToSave.size());

            // 5. 이관 및 집계가 끝난 원본 데이터는 Redis에서 삭제 (메모리 확보)
            redisRepository.deleteAll(validDataList);
            log.info("[BATCH TASK] 집계 완료된 {}개의 원본 데이터를 Redis에서 삭제했습니다.", validDataList.size());
            
        } catch (Exception e) {
            log.error("[BATCH TASK] 데이터 집계/이관 중 오류 발생: {}", e.getMessage(), e);
        }
    }

    /**
     * 매일 자정에 전날 통계를 Discord로 발송합니다.
     * cron 표현식은 application.yaml의 smartfarm.batch.report-cron으로 설정합니다.
     */
    @Scheduled(cron = "${smartfarm.batch.report-cron}")
    public void sendDailyReport() {
        LocalDate yesterday    = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        LocalDateTime dayStart = yesterday.atStartOfDay();
        LocalDateTime dayEnd   = yesterday.atTime(23, 59, 59, 999_999_999);

        List<String> deviceIds = deviceConfigRepository.findAllDeviceIds();

        if (deviceIds.isEmpty()) {
            log.info("[DAILY REPORT] 등록된 기기가 없어 리포트를 생략합니다.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📊 **[스마트팜 일간 리포트] %s**\n", yesterday));

        for (String deviceId : deviceIds) {
            SensorStatisticsDto stats = mysqlRepository.getSensorStatistics(deviceId, dayStart, dayEnd);

            if (stats == null) {
                sb.append(String.format("\n🖥️ **%s** — 데이터 없음\n", deviceId));
                continue;
            }

            sb.append(String.format(
                    "\n🖥️ **%s**\n" +
                    "  🌡️ 온도  최고: %.1f°C  최저: %.1f°C  평균: %.1f°C\n" +
                    "  💧 평균 습도: %.1f%%\n",
                    deviceId,
                    stats.getMaxTemperature(),
                    stats.getMinTemperature(),
                    stats.getAvgTemperature(),
                    stats.getAvgHumidity()
            ));
        }

        discordNotificationService.sendMessage(sb.toString());
        log.info("[DAILY REPORT] {} 일간 리포트 발송 완료 (기기 {}대)", yesterday, deviceIds.size());
    }

    /**
     * 매일 새벽 2시에 1개월 이상 지난 SensorHistory 데이터를 삭제합니다.
     * 데이터 보존 정책: 30일 초과 데이터 자동 정리
     */
    @Transactional
    @Scheduled(cron = "0 0 2 * * *")
    public void deleteOldSensorHistory() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusMonths(1);
        log.info("[BATCH TASK] 데이터 보존 정책 실행 - {}  이전 데이터 삭제 시작", cutoff);
        mysqlRepository.deleteByTimestampBefore(cutoff);
        log.info("[BATCH TASK] 1개월 이상 지난 SensorHistory 데이터 삭제 완료");
    }

    /**
     * 매일 새벽 3시에 소프트 딜리트된 지 1주일 이상 지난 SensorHistory 데이터를 하드 딜리트합니다.
     * 기기 삭제 후 1주일간 데이터를 보존하는 정책 처리
     */
    @Transactional
    @Scheduled(cron = "0 0 3 * * *")
    public void hardDeleteSoftDeletedHistory() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneId.of("Asia/Seoul")).minusWeeks(1);
        log.info("[BATCH TASK] 소프트 딜리트 데이터 정리 실행 - {} 이전 소프트 딜리트 데이터 하드 딜리트 시작", cutoff);
        mysqlRepository.deleteByDeletedAtBefore(cutoff);
        log.info("[BATCH TASK] 소프트 딜리트된 지 1주일 이상 지난 SensorHistory 데이터 하드 딜리트 완료");
    }
}
