package com.smartfarm.server.common.exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 프론트엔드/클라이언트에게 반환할 공통 에러 JSON 응답 객체
 */
@Getter
@Builder
public class ErrorResponse {
    private final String timestamp = LocalDateTime.now().toString();
    private final int status;       // HTTP 상태 코드 (예: 400)
    private final String error;     // 에러 이름 (예: BAD_REQUEST)
    private final String code;      // 비즈니스 에러 코드 (예: E001)
    private final String message;   // 상세 메시지 (예: 온도는 150도를 넘을 수 없습니다)
    private final Object details;   // 추가적인 에러 상세 정보 (Map 등)

    public static ErrorResponse of(ErrorCode errorCode, Object details) {
        return ErrorResponse.builder()
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .details(details)
                .build();
    }
    
    public static ErrorResponse of(ErrorCode errorCode, String customMessage) {
        return ErrorResponse.builder()
                .status(errorCode.getStatus().value())
                .error(errorCode.getStatus().name())
                .code(errorCode.getCode())
                .message(customMessage)
                .build();
    }
}
