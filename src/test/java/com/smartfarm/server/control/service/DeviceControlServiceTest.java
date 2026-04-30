package com.smartfarm.server.control.service;

import com.smartfarm.server.control.entity.CommandStatus;
import com.smartfarm.server.control.entity.DeviceControlCommand;
import com.smartfarm.server.control.repository.DeviceControlCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeviceControlServiceTest {

    private static final String DEVICE_ID = "TEST-PC-001";

    @Autowired
    private DeviceControlService deviceControlService;

    @Autowired
    private DeviceControlCommandRepository commandRepository;

    @BeforeEach
    void setUp() {
        commandRepository.deleteAll();
    }

    @Test
    void getDeviceStateUsesLatestAcknowledgedCommandPerActuatorType() {
        LocalDateTime baseTime = LocalDateTime.now().minusMinutes(30);
        saveCommand("HEATER_ON", baseTime);
        saveCommand("HUMIDIFIER_ON", baseTime.plusMinutes(1));

        saveCommand("COOLING_FAN_ON", baseTime.plusMinutes(2));
        saveCommand("COOLING_FAN_OFF", baseTime.plusMinutes(3));
        saveCommand("COOLING_FAN_ON", baseTime.plusMinutes(4));
        saveCommand("COOLING_FAN_OFF", baseTime.plusMinutes(5));
        saveCommand("COOLING_FAN_ON", baseTime.plusMinutes(6));
        saveCommand("COOLING_FAN_OFF", baseTime.plusMinutes(7));

        Map<String, Boolean> state = deviceControlService.getDeviceState(DEVICE_ID);

        assertThat(state.get("coolingFanOn")).isFalse();
        assertThat(state.get("heaterOn")).isTrue();
        assertThat(state.get("humidifierOn")).isTrue();
    }

    private void saveCommand(String commandType, LocalDateTime createdAt) {
        commandRepository.save(DeviceControlCommand.builder()
                .deviceId(DEVICE_ID)
                .commandType(commandType)
                .status(CommandStatus.ACKNOWLEDGED)
                .createdAt(createdAt)
                .build());
    }
}
