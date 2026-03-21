package com.smartfarm.server.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.smartfarm.server.dto.SensorStatisticsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

// Querydsl 자동 생성 클래스인 QSensorHistory를 import 합니다.
// (import 에러가 난다면, 우측 Gradle 탭 -> Tasks -> build -> build 를 더블클릭하여 Q클래스를 생성해주세요!)
import static com.smartfarm.server.entity.QSensorHistory.sensorHistory;

@Repository
@RequiredArgsConstructor
public class SensorHistoryRepositoryCustomImpl implements SensorHistoryRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public SensorStatisticsDto getSensorStatistics(String deviceId, LocalDateTime start, LocalDateTime end) {
        
        // Querydsl을 사용하여 SQL(JPQL)을 자바 코드로 작성합니다.
        // 장점: 오타가 나면 컴파일 에러로 바로 알려주고, 동적 쿼리 작성이 매우 쉽습니다.
        return queryFactory
                .select(Projections.constructor(SensorStatisticsDto.class,
                        sensorHistory.deviceId,
                        sensorHistory.temperature.max(),
                        sensorHistory.temperature.min(),
                        sensorHistory.temperature.avg(),
                        sensorHistory.humidity.avg()
                ))
                .from(sensorHistory)
                .where(
                        // WHERE device_id = ? AND timestamp BETWEEN ? AND ? AND deleted_at IS NULL
                        sensorHistory.deviceId.eq(deviceId),
                        sensorHistory.timestamp.between(start, end),
                        sensorHistory.deletedAt.isNull()
                )
                .groupBy(sensorHistory.deviceId) // 디바이스별로 묶어서 통계를 냅니다.
                .fetchOne(); // 결과가 1건(또는 null)이므로 fetchOne()을 사용합니다.
    }
}
