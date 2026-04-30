package com.smartfarm.server.config;

import com.smartfarm.server.audit.entity.AuditLog;
import com.smartfarm.server.control.entity.CommandStatus;
import com.smartfarm.server.control.entity.DeviceControlCommand;
import com.smartfarm.server.control.repository.DeviceControlCommandRepository;
import com.smartfarm.server.device.entity.DeviceConfig;
import com.smartfarm.server.audit.repository.AuditLogRepository;
import com.smartfarm.server.device.repository.DeviceConfigRepository;
import com.smartfarm.server.device.filter.DeviceApiKeyAuthFilter;
import com.smartfarm.server.sensor.dto.SensorRequestDto;
import com.smartfarm.server.sensor.dto.SensorResponseDto;
import com.smartfarm.server.sensor.service.SensorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DeviceApiKeyAuthFilter 통합 테스트
 * API 키 인증 실패 시나리오와 감사 로그 연동을 검증합니다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class DeviceApiKeyAuthFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeviceConfigRepository deviceConfigRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private DeviceControlCommandRepository commandRepository;

    @Autowired
    private CacheManager cacheManager;

    @MockBean
    private SensorService sensorService;

    private static final String TEST_DEVICE_ID = "TEST-PC-001";
    private static final String OTHER_DEVICE_ID = "OTHER-PC-001";
    private String testApiKey;
    private static final String PROTECTED_ENDPOINT = "/api/sensor/data";

    @BeforeEach
    void setUp() {
        // 캐시 초기화 (이전 테스트의 캐시된 값이 다음 테스트에 영향을 주지 않도록)
        var cache = cacheManager.getCache("deviceConfigView");
        if (cache != null) {
            cache.clear();
        }

        auditLogRepository.deleteAll();
        commandRepository.deleteAll();
        deviceConfigRepository.deleteAll();

        // 테스트용 기기 설정 등록
        // apiKey는 @PrePersist에서 자동으로 생성됨
        DeviceConfig config = DeviceConfig.builder()
                .deviceId(TEST_DEVICE_ID)
                .build();

        deviceConfigRepository.save(config);
        deviceConfigRepository.save(DeviceConfig.builder()
                .deviceId(OTHER_DEVICE_ID)
                .build());

        // 저장된 설정에서 생성된 API 키를 조회하여 테스트에 사용
        DeviceConfig retrievedConfig = deviceConfigRepository.findByDeviceId(TEST_DEVICE_ID)
                .orElseThrow(() -> new RuntimeException("Device config not found after save"));
        testApiKey = retrievedConfig.getApiKey();

        when(sensorService.processSensorData(any(SensorRequestDto.class)))
                .thenReturn(SensorResponseDto.builder()
                        .status("SUCCESS")
                        .message("Data processed successfully")
                        .build());
    }

    @Test
    void testMissingDeviceIdHeader() throws Exception {
        // When: X-Device-Id 헤더가 없이 요청
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey)
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc("UNKNOWN", PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("AUTH_FAILURE");
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getErrorMessage()).contains("Missing required headers");
    }

    @Test
    void testMissingApiKeyHeader() throws Exception {
        // When: X-Api-Key 헤더가 없이 요청
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("AUTH_FAILURE");
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getErrorMessage()).contains("Missing required headers");
    }

    @Test
    void testBlankDeviceIdHeader() throws Exception {
        // When: X-Device-Id이 빈 값으로 요청
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, "   ")
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey)
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("AUTH_FAILURE");
    }

    @Test
    void testBlankApiKeyHeader() throws Exception {
        // When: X-Api-Key가 빈 값으로 요청
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, "   ")
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("AUTH_FAILURE");
    }

    @Test
    void testInvalidApiKey() throws Exception {
        // When: 잘못된 API 키로 요청
        String invalidApiKey = UUID.randomUUID().toString();
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, invalidApiKey)
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("AUTH_FAILURE");
        assertThat(log.getSuccess()).isFalse();
        assertThat(log.getErrorMessage()).contains("Invalid API key");
    }

    @Test
    void testUnregisteredDevice() throws Exception {
        // Given: 등록되지 않은 기기
        String unknownDeviceId = "UNKNOWN-DEVICE-123";
        String anyApiKey = UUID.randomUUID().toString();

        // When: 미등록 기기로 요청
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, unknownDeviceId)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, anyApiKey)
                .contentType("application/json")
                .content("{\"deviceId\":\"UNKNOWN-DEVICE-123\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(unknownDeviceId, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);

        AuditLog log = logs.get(0);
        assertThat(log.getEventType()).isEqualTo("AUTH_FAILURE");
        assertThat(log.getSuccess()).isFalse();
    }

    @Test
    void testSuccessfulAuthentication() throws Exception {
        // When: 올바른 헤더와 API 키로 요청 (요청 본문이 유효해야 성공 처리)
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey)
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                // 인증이 통과되면 컨트롤러로 진행되어 200 OK 반환 또는 다른 상태 코드
                .andExpect(status().isOk()); // 또는 성공적으로 처리된 상태

        // Then: AUTH_FAILURE 감사 로그가 없어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).isEmpty();
    }

    @Test
    void rejectsSensorDataWhenHeaderDeviceDoesNotMatchBodyDevice() throws Exception {
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey)
                .contentType("application/json")
                .content("{\"deviceId\":\"" + OTHER_DEVICE_ID + "\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isForbidden());

        verify(sensorService, never()).processSensorData(any(SensorRequestDto.class));
    }

    @Test
    void rejectsPendingCommandLookupForDifferentDevice() throws Exception {
        mockMvc.perform(get("/api/device-control/pending")
                .param("deviceId", OTHER_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsDeviceCommandStreamForDifferentDevice() throws Exception {
        mockMvc.perform(get("/api/sse/device-command-stream")
                .param("deviceId", OTHER_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAckForCommandOwnedByDifferentDevice() throws Exception {
        DeviceControlCommand command = commandRepository.save(DeviceControlCommand.builder()
                .deviceId(OTHER_DEVICE_ID)
                .commandType("COOLING_FAN_ON")
                .status(CommandStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post("/api/device-control/ack")
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, testApiKey)
                .contentType("application/json")
                .content("{\"commandId\":" + command.getId() + "}"))
                .andExpect(status().isForbidden());

        DeviceControlCommand reloaded = commandRepository.findById(command.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CommandStatus.PENDING);
    }

    @Test
    void testMultipleAuthFailures() throws Exception {
        // When: 같은 기기에서 여러 번 인증 실패
        String invalidApiKey = UUID.randomUUID().toString();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(PROTECTED_ENDPOINT)
                    .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                    .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, invalidApiKey)
                    .contentType("application/json")
                    .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                    .andExpect(status().isUnauthorized());
        }

        // Then: 3개의 인증 실패 로그가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(3);
        assertThat(logs).allMatch(log -> log.getEventType().equals("AUTH_FAILURE"));
    }

    @Test
    void testAuthFailureWithClientIp() throws Exception {
        // When: 특정 클라이언트 IP로부터 인증 실패 요청
        mockMvc.perform(post(PROTECTED_ENDPOINT)
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, "wrong-key")
                .header("X-Forwarded-For", "192.168.1.100")
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\",\"cpu_temperature\":25.5,\"humidity\":45.3,\"timestamp\":" + System.currentTimeMillis() + "}"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 클라이언트 IP가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getIpAddress()).contains("192.168.1.100");
    }

    @Test
    void testProtectedPathUnprotectedPath() throws Exception {
        // When: 보호되지 않는 경로로 요청 (헤더 없이)
        mockMvc.perform(post("/api/device/register")
                .contentType("application/json")
                .content("{\"deviceId\":\"TEST-PC-001\"}"))
                // 헤더 부재로 인한 401이 아닌 다른 상태 코드 (필터에서 검증 안 함)
                .andExpect(status().isBadRequest()); // 또는 다른 상태 (본문 형식 등)

        // Then: 인증 실패 로그가 없어야 함 (필터가 이 경로를 보호하지 않음)
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).isEmpty();
    }

    @Test
    void testDeviceControlPendingEndpoint() throws Exception {
        // When: /api/device-control/pending 보호 경로에서 헤더 누락
        mockMvc.perform(post("/api/device-control/pending")
                .contentType("application/json"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("AUTH_FAILURE");
    }

    @Test
    void testDeviceControlAckEndpoint() throws Exception {
        // When: /api/device-control/ack 보호 경로에서 잘못된 API 키
        mockMvc.perform(post("/api/device-control/ack")
                .header(DeviceApiKeyAuthFilter.HEADER_DEVICE_ID, TEST_DEVICE_ID)
                .header(DeviceApiKeyAuthFilter.HEADER_API_KEY, "invalid")
                .contentType("application/json"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findByDeviceIdOrderByCreatedAtDesc(TEST_DEVICE_ID, PageRequest.of(0, 10)).getContent();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("AUTH_FAILURE");
    }

    @Test
    void testSseDeviceCommandStreamEndpoint() throws Exception {
        // When: /api/sse/device-command-stream 보호 경로에서 헤더 누락
        mockMvc.perform(post("/api/sse/device-command-stream"))
                .andExpect(status().isUnauthorized());

        // Then: 감사 로그에 인증 실패가 기록되어야 함
        List<AuditLog> logs = auditLogRepository.findAll();
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getEventType()).isEqualTo("AUTH_FAILURE");
    }
}
