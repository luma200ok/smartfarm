package com.smartfarm.server.repository;

import com.smartfarm.server.entity.SensorHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * MySQL 데이터베이스 접근을 위한 JPA Repository
 */
@Repository
public interface SensorHistoryRepository extends JpaRepository<SensorHistory, Long> {
}
