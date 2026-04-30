package com.smartfarm.server.device.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartfarm.server.common.exception.CustomException;
import com.smartfarm.server.device.dto.DeviceConfigView;
import com.smartfarm.server.common.exception.ErrorCode;
import com.smartfarm.server.audit.service.AuditLogService;
import com.smartfarm.server.device.service.DeviceConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * PC 클라이언트 전용 엔드포인트에 대한 API 키 인증 필터.
 *
 * <p>PC 클라이언트는 모든 요청에 다음 두 헤더를 포함해야 합니다:</p>
 * <ul>
 *   <li>{@code X-Device-Id} : 기기 고유 ID (예: WINDOWS-PC-01)</li>
 *   <li>{@code X-Api-Key}   : 기기 등록 시 발급된 UUID 형식의 API 키</li>
 * </ul>
 *
 * <p>헤더가 없거나 값이 일치하지 않으면 HTTP 401 Unauthorized를 반환합니다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_DEVICE_ID = "X-Device-Id";
    public static final String HEADER_API_KEY   = "X-Api-Key";
    public static final String AUTHENTICATED_DEVICE_ID_ATTRIBUTE =
            DeviceApiKeyAuthFilter.class.getName() + ".AUTHENTICATED_DEVICE_ID";

    /** API 키 인증이 필요한 PC 클라이언트 전용 경로 */
    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/sensor/",
            "/api/device-control/pending",
            "/api/device-control/ack",
            "/api/sse/device-command-stream"
    );

    private final DeviceConfigService deviceConfigService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // 보호 대상 경로가 아니면 통과
        if (!isProtectedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String deviceId = request.getHeader(HEADER_DEVICE_ID);
        String apiKey   = request.getHeader(HEADER_API_KEY);

        // 헤더 누락 검사
        if (deviceId == null || deviceId.isBlank() || apiKey == null || apiKey.isBlank()) {
            log.warn(">>> [API Key Auth] 헤더 누락 — path={}, X-Device-Id={}", path, deviceId);
            String reason = "Missing required headers: X-Device-Id or X-Api-Key";
            auditLogService.logAuthFailure(deviceId != null ? deviceId : "UNKNOWN", reason, getClientIp(request));
            sendUnauthorized(response, "X-Device-Id, X-Api-Key 헤더가 필요합니다.");
            return;
        }

        // API 키 유효성 검증
        // DeviceConfigService.validateApiKey() 내부에서 this.getDeviceConfig() 를 호출하면
        // self-invocation 이 되어 Spring 프록시를 우회 → @Cacheable 무시, 항상 DB 직접 조회.
        // Filter 에서 getDeviceConfig() 를 직접 외부 호출하면 프록시가 정상 작동해 캐시 히트.
        boolean valid;
        try {
            DeviceConfigView config = deviceConfigService.getDeviceConfig(deviceId);
            valid = config.apiKey() != null && config.apiKey().equals(apiKey);
        } catch (Exception ex) {
            log.error(">>> [API Key Auth] 인증 처리 중 오류 — deviceId={}, path={}, error={}", deviceId, path, ex.getMessage(), ex);
            String reason = "Authentication processing error: " + ex.getMessage();
            auditLogService.logAuthFailure(deviceId, reason, getClientIp(request));
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"status\":500,\"message\":\"인증 처리 중 서버 오류가 발생했습니다.\"}");
            return;
        }

        if (!valid) {
            log.warn(">>> [API Key Auth] 인증 실패 — deviceId={}, path={}", deviceId, path);
            String reason = "Invalid API key provided";
            auditLogService.logAuthFailure(deviceId, reason, getClientIp(request));
            sendUnauthorized(response, ErrorCode.INVALID_API_KEY.getMessage());
            return;
        }

        log.debug(">>> [API Key Auth] 인증 성공 — deviceId={}, path={}", deviceId, path);
        request.setAttribute(AUTHENTICATED_DEVICE_ID_ATTRIBUTE, deviceId);
        filterChain.doFilter(request, response);
    }

    public static void assertAuthenticatedDevice(HttpServletRequest request, String requestedDeviceId) {
        Object authenticated = request.getAttribute(AUTHENTICATED_DEVICE_ID_ATTRIBUTE);
        if (!(authenticated instanceof String authenticatedDeviceId) || !authenticatedDeviceId.equals(requestedDeviceId)) {
            throw new CustomException(ErrorCode.DEVICE_ACCESS_DENIED);
        }
    }

    private boolean isProtectedPath(String path) {
        return PROTECTED_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = Map.of(
                "status", 401,
                "code",   ErrorCode.INVALID_API_KEY.getCode(),
                "message", message
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
