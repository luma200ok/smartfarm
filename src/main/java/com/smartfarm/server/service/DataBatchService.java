package com.smartfarm.server.service;

import com.smartfarm.server.entity.SensorData;
import com.smartfarm.server.entity.SensorHistory;
import com.smartfarm.server.repository.SensorHistoryRepository;
import com.smartfarm.server.repository.SensorRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataBatchService {

    private final SensorRedisRepository redisRepository;
    private final SensorHistoryRepository mysqlRepository;

    /**
     * @Scheduled를 사용한 주기적 작업
     * 1분마다(60,000ms) 실행되며, Redis에 캐싱된 현재 최신 데이터를 MySQL로 이관합니다.
     */
    @Scheduled(fixedRate = 60000) 
    public void migrateDataFromRedisToMySql() {
        log.info("[BATCH TASK] 1분 주기 데이터 이관 배치 시작 - Redis -> MySQL");
        
        // 1. Redis에 저장된 모든 센서 데이터를 가져옵니다.
        Iterable<SensorData> redisDataList = redisRepository.findAll();
        
        List<SensorHistory> historyList = new ArrayList<>();
        
        for (SensorData redisData : redisDataList) {
            // 방어 로직: Redis에서 만료(TTL) 과정 중이거나 비정상적인 null 데이터가 섞여 들어올 수 있으므로 체크합니다.
            if (redisData == null || redisData.getDeviceId() == null) {
                continue; 
            }

            // 2. Redis 데이터를 MySQL 전용 Entity(SensorHistory)로 변환
            SensorHistory history = SensorHistory.builder()
                    .deviceId(redisData.getDeviceId())
                    .temperature(redisData.getTemperature())
                    .humidity(redisData.getHumidity())
                    .timestamp(redisData.getTimestamp())
                    .build();
            historyList.add(history);
        }

        // 3. MySQL에 일괄 저장 (Batch Insert)
        if (!historyList.isEmpty()) {
            mysqlRepository.saveAll(historyList);
            log.info("[BATCH TASK] 총 {}개의 센서 데이터를 MySQL에 저장했습니다.", historyList.size());
        } else {
            log.info("[BATCH TASK] 이관할 센서 데이터가 없습니다.");
        }
        
        log.info("[BATCH TASK] 배치 작업 완료.");
    }
}
