package com.smartfarm.server.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 프로젝트에서 발생하는 모든 예외 코드를 한 곳에서 관리하는 Enum
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "E001", "입력값이 올바르지 않습니다."),
    DEVICE_NOT_FOUND(HttpStatus.BAD_REQUEST, "E002", "등록되지 않은 기기입니다."),
    COMMAND_NOT_FOUND(HttpStatus.BAD_REQUEST, "E003", "존재하지 않는 명령입니다."),
    INVALID_COMMAND_TYPE(HttpStatus.BAD_REQUEST, "E004", "유효하지 않은 명령 종류입니다."),

    // 409 Conflict
    DEVICE_ALREADY_EXISTS(HttpStatus.CONFLICT, "E005", "이미 등록된 기기입니다."),

    // 429 Too Many Requests
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "E006", "요청이 너무 많습니다. 잠시 후 다시 시도하세요."),

    // 401 Unauthorized
    INVALID_API_KEY(HttpStatus.UNAUTHORIZED, "A001", "유효하지 않은 API 키입니다. X-Device-Id, X-Api-Key 헤더를 확인하세요."),

    // 403 Forbidden
    DEVICE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "A002", "해당 기기에 대한 접근 권한이 없습니다."),
    
    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "서버 내부 오류가 발생했습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "S002", "데이터베이스 처리 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
