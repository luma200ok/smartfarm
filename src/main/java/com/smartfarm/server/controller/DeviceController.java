package com.smartfarm.server.controller;

import com.smartfarm.server.config.DeviceRegisterRateLimiter;
import com.smartfarm.server.dto.DeviceRegisterRequestDto;
import com.smartfarm.server.dto.DeviceRegisterResponseDto;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.service.DeviceConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
@Tag(name = "5. 기기 등록 API", description = "신규 PC 기기 자동 등록 및 API 키 발급")
public class DeviceController {

    private final DeviceConfigService deviceConfigService;
    private final DeviceRegisterRateLimiter deviceRegisterRateLimiter;

    @Operation(
            summary = "신규 기기 자동 등록",
            description = """
                    신규 PC 클라이언트가 처음 서버에 접속할 때 호출합니다.
                    - 인증 불필요 (API 키가 없는 신규 기기 전용)
                    - deviceId 만으로 DeviceConfig를 생성하고 API 키를 즉시 발급합니다.
                    - 발급된 API 키는 .env 파일에 저장하세요. 이후 재조회가 불가능합니다.
                    - 이미 등록된 deviceId 로 요청하면 409 Conflict 를 반환합니다.
                    - IP당 1분에 최대 5회만 허용합니다.
                    """)
    @PostMapping("/register")
    public ResponseEntity<DeviceRegisterResponseDto> register(
            @Valid @RequestBody DeviceRegisterRequestDto request,
            HttpServletRequest httpRequest) {

        String ip = getClientIp(httpRequest);
        if (!deviceRegisterRateLimiter.tryAcquire(ip)) {
            throw new CustomException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }

        return ResponseEntity.ok(deviceConfigService.registerDevice(request));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
