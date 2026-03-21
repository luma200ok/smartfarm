package com.smartfarm.server.repository;

import com.smartfarm.server.entity.ControlEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlEventLogRepository extends JpaRepository<ControlEventLog, Long> {

    Page<ControlEventLog> findByDeviceIdOrderByTimestampDesc(String deviceId, Pageable pageable);
}