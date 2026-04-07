package com.smartfarm.server.common.exception;

import com.smartfarm.server.audit.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * 전역 예외 처리기 고도화
 * 모든 예외를 일관된 ErrorResponse 객체로 래핑하여 프론트엔드에 반환합니다.
 * 권한 거부 예외는 감사 로그에 기록합니다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuditLogService auditLogService;

    /**
     * 1. 비즈니스 로직 처리 중 발생한 커스텀 예외 처리
     * 권한 거부 및 인증 실패 에러는 감사 로그에 기록합니다.
     */
    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("CustomException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        ErrorResponse response = ErrorResponse.of(errorCode, e.getMessage());

        // 권한 거부 및 인증 관련 에러 로깅
        if (errorCode == ErrorCode.DEVICE_ACCESS_DENIED || errorCode == ErrorCode.INVALID_API_KEY) {
            String deviceId = getDeviceIdFromContext();
            String performedBy = getPrincipalName();
            String ipAddress = getClientIp();

            if (errorCode == ErrorCode.DEVICE_ACCESS_DENIED) {
                auditLogService.logPermissionDenied(
                    deviceId != null ? deviceId : "UNKNOWN",
                    null,
                    performedBy,
                    "Device access denied",
                    ipAddress
                );
            }
        }

        return new ResponseEntity<>(response, errorCode.getStatus());
    }

    /**
     * 2. DTO @Valid 유효성 검사 실패 시 발생하는 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException e) {
        log.error("MethodArgumentNotValidException: {}", e.getMessage());
        
        // 발생한 에러들을 Map(필드명 : 에러 메시지) 형태로 정리
        // getFieldErrors()를 사용하면 FieldError 타입이 보장되어 ClassCastException 위험이 없습니다.
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach((fieldError) -> {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        });
        
        ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, errors);
        return new ResponseEntity<>(response, ErrorCode.INVALID_INPUT_VALUE.getStatus());
    }

    /**
     * 3. API 파라미터 타입이 맞지 않을 때 발생하는 예외 처리 (예: /api?deviceId=1 에 String 대신 객체가 올 때)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e) {
        log.error("MethodArgumentTypeMismatchException: {}", e.getMessage());
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INVALID_INPUT_VALUE, "파라미터 타입이 일치하지 않습니다.");
        return new ResponseEntity<>(response, ErrorCode.INVALID_INPUT_VALUE.getStatus());
    }

    /**
     * 4. 정적 리소스를 찾지 못한 경우 (favicon.ico 등) — ERROR 로그 없이 404 반환
     */
    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        log.debug("Static resource not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    /**
     * 4-1. SSE 연결 타임아웃 — 정상적인 동작이므로 로그 없이 무시
     * GlobalExceptionHandler가 text/event-stream 응답에 JSON을 쓰려다 터지는 문제 방지
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    protected ResponseEntity<Void> handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        log.debug("SSE connection timed out (정상 종료)");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * 4-2. SSE 클라이언트 연결 끊김(Broken pipe) — 정상 동작이므로 무시
     * 브라우저가 페이지를 닫으면 발생하며, 에러 응답을 쓸 수 없으므로 반환값 없음
     */
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    protected void handleAsyncRequestNotUsableException(
            org.springframework.web.context.request.async.AsyncRequestNotUsableException e) {
        log.debug("SSE client disconnected (Broken pipe) — 정상 종료");
    }

    /**
     * 5. 위에서 잡히지 않은 모든 알 수 없는 에러 처리 (최후의 보루)
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("Unhandled Exception: {}", e.getMessage(), e);
        ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
        return new ResponseEntity<>(response, ErrorCode.INTERNAL_SERVER_ERROR.getStatus());
    }

    /**
     * 현재 HTTP 요청의 클라이언트 IP를 추출합니다.
     * X-Forwarded-For, X-Real-IP 헤더를 우선적으로 확인하고, 없으면 RemoteAddr을 반환합니다.
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return "UNKNOWN";
            }

            HttpServletRequest request = attributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }

            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }

            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("Failed to extract client IP", e);
            return "UNKNOWN";
        }
    }

    /**
     * 현재 인증된 사용자의 이름을 반환합니다.
     */
    private String getPrincipalName() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Failed to extract principal name", e);
        }
        return "SYSTEM";
    }

    /**
     * 요청 컨텍스트에서 deviceId를 추출합니다.
     * 헤더나 파라미터에서 찾을 수 있습니다.
     */
    private String getDeviceIdFromContext() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }

            HttpServletRequest request = attributes.getRequest();
            // X-Device-Id 헤더 확인
            String deviceId = request.getHeader("X-Device-Id");
            if (deviceId != null && !deviceId.isEmpty()) {
                return deviceId;
            }

            // 요청 파라미터에서 deviceId 찾기
            deviceId = request.getParameter("deviceId");
            if (deviceId != null && !deviceId.isEmpty()) {
                return deviceId;
            }
        } catch (Exception e) {
            log.debug("Failed to extract deviceId from context", e);
        }
        return null;
    }
}
