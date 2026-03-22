package com.smartfarm.server.controller;

import com.smartfarm.server.service.SseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
@Tag(name = "4. 실시간 SSE API", description = "Server-Sent Events 기반 실시간 센서 데이터 스트림 및 PC 클라이언트 제어 명령 스트림")
public class SseController {

    private final SseEmitterService sseEmitterService;

    @Operation(
            summary = "대시보드 실시간 센서 데이터 구독",
            description = "브라우저에서 특정 기기의 센서 데이터를 실시간으로 수신합니다. 인증 필요.")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String deviceId) {
        return sseEmitterService.subscribe(deviceId);
    }

    @Operation(
            summary = "PC 클라이언트 제어 명령 스트림 구독",
            description = "PC 클라이언트가 서버로부터 원격 제어 명령을 실시간으로 수신합니다. "
                        + "대시보드에서 명령이 발송되면 이 스트림을 통해 즉시 전달됩니다. "
                        + "PC 클라이언트는 명령 수신 후 /api/device-control/ack 로 실행 확인을 전송해야 합니다. "
                        + "인증 불필요 (PC 클라이언트 전용).")
    @GetMapping(value = "/device-command-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeDeviceCommandStream(@RequestParam String deviceId) {
        return sseEmitterService.subscribeDevice(deviceId);
    }
}
