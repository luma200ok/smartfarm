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
@Tag(name = "4. 실시간 SSE API", description = "Server-Sent Events 기반 실시간 센서 데이터 스트림")
public class SseController {

    private final SseEmitterService sseEmitterService;

    @Operation(summary = "기기 실시간 데이터 구독")
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestParam String deviceId) {
        return sseEmitterService.subscribe(deviceId);
    }
}
