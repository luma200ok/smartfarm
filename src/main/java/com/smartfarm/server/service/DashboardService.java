package com.smartfarm.server.service;

import com.smartfarm.server.dto.SensorHistoryResponseDto;
import com.smartfarm.server.repository.SensorHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 데이터 변경(CUD) 없이 조회(Read)만 하므로 성능 최적화를 위해 읽기 전용으로 설정합니다.
public class DashboardService {

    private final SensorHistoryRepository historyRepository;

    /**
     * 특정 기기의 최근 이력 데이터를 페이징하여 조회합니다.
     */
    public Page<SensorHistoryResponseDto> getSensorHistory(String deviceId, Pageable pageable) {
        // JPA Repository에서 데이터를 Page 객체로 가져온 후, 
        // 외부 노출 방지를 위해 Entity(SensorHistory)를 DTO(SensorHistoryResponseDto)로 변환(map)합니다.
        return historyRepository.findByDeviceIdOrderByTimestampDesc(deviceId, pageable)
                .map(SensorHistoryResponseDto::from);
    }
}
