package com.smartfarm.server.repository;

import com.smartfarm.server.entity.ControlEventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlEventLogRepository extends JpaRepository<ControlEventLog, Long> {
}