package com.smartfarm.server.sensor.repository;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.DateTemplate;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.smartfarm.server.sensor.dto.DailyStatisticsDto;
import com.smartfarm.server.sensor.dto.SensorStatisticsDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Querydsl 자동 생성 클래스인 QSensorHistory를 import 합니다.
// (import 에러가 난다면, 우측 Gradle 탭 -> Tasks -> build -> build 를 더블클릭하여 Q클래스를 생성해주세요!)
import static com.smartfarm.server.sensor.entity.QSensorHistory.sensorHistory;

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

    @Override
    public Map<String, SensorStatisticsDto> getAllDevicesStatistics(List<String> deviceIds, LocalDateTime start, LocalDateTime end) {
        // 단일 쿼리로 모든 기기의 통계를 한 번에 조회합니다. (N+1 방지)
        List<SensorStatisticsDto> results = queryFactory
                .select(Projections.constructor(SensorStatisticsDto.class,
                        sensorHistory.deviceId,
                        sensorHistory.temperature.max(),
                        sensorHistory.temperature.min(),
                        sensorHistory.temperature.avg(),
                        sensorHistory.humidity.avg()
                ))
                .from(sensorHistory)
                .where(
                        sensorHistory.deviceId.in(deviceIds),
                        sensorHistory.timestamp.between(start, end),
                        sensorHistory.deletedAt.isNull()
                )
                .groupBy(sensorHistory.deviceId)
                .fetch();

        return results.stream()
                .collect(Collectors.toMap(SensorStatisticsDto::getDeviceId, dto -> dto));
    }

    @Override
    public List<DailyStatisticsDto> getDailyStatistics(String deviceId, LocalDateTime start, LocalDateTime end) {

        // timestamp 를 날짜(DATE)로 변환하는 표현식
        // java.sql.Date 로 받아야 런타임 타입 불일치가 발생하지 않음 (DailyStatisticsDto 생성자에서 toLocalDate() 변환)
        DateTemplate<java.sql.Date> dateExpr = Expressions.dateTemplate(
                java.sql.Date.class, "DATE({0})", sensorHistory.timestamp
        );

        return queryFactory
                .select(Projections.constructor(DailyStatisticsDto.class,
                        dateExpr,
                        sensorHistory.temperature.avg(),
                        sensorHistory.temperature.max(),
                        sensorHistory.temperature.min(),
                        sensorHistory.humidity.avg(),
                        sensorHistory.count()
                ))
                .from(sensorHistory)
                .where(
                        sensorHistory.deviceId.eq(deviceId),
                        sensorHistory.timestamp.between(start, end),
                        sensorHistory.deletedAt.isNull()
                )
                .groupBy(dateExpr)
                .orderBy(dateExpr.asc())
                .fetch();
    }
}
