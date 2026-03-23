package com.smartfarm.server.device.repository;

import com.smartfarm.server.device.entity.DeviceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeviceConfigRepository extends JpaRepository<DeviceConfig, Long> {
    Optional<DeviceConfig> findByDeviceId(String deviceId);

    @Query("SELECT d.deviceId FROM DeviceConfig d ORDER BY d.deviceId ASC")
    List<String> findAllDeviceIds();
}