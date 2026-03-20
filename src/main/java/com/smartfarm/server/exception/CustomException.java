package com.smartfarm.server.exception;

import lombok.Getter;

/**
 * 비즈니스 로직 처리 중 의도적으로 발생시키는 커스텀 예외 클래스
 */
@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public CustomException(ErrorCode errorCode, String customMessage) {
        super(customMessage);
        this.errorCode = errorCode;
    }
}
