package com.smartfarm.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseEmitterService {

    // deviceId → 연결된 emitter 목록 (다중 브라우저 탭 지원)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30분

    /**
     * 클라이언트가 특정 기기를 구독할 때 호출됩니다.
     */
    public SseEmitter subscribe(String deviceId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        emitters.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("[SSE] {} 기기 구독 시작 (현재 구독자: {}명)", deviceId, emitters.get(deviceId).size());

        // 연결 종료/타임아웃/에러 시 자동 제거
        Runnable cleanup = () -> removeEmitter(deviceId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        // 연결 직후 dummy 이벤트로 연결 확인
        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            removeEmitter(deviceId, emitter);
        }

        return emitter;
    }

    /**
     * 특정 기기를 구독 중인 모든 클라이언트에게 데이터를 전송합니다.
     */
    public void sendToDevice(String deviceId, Object data) {
        List<SseEmitter> deviceEmitters = emitters.get(deviceId);
        if (deviceEmitters == null || deviceEmitters.isEmpty()) return;

        deviceEmitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("sensor").data(data));
                return false;
            } catch (IOException e) {
                log.debug("[SSE] {} 기기 emitter 전송 실패, 제거합니다.", deviceId);
                return true;
            }
        });
    }

    private void removeEmitter(String deviceId, SseEmitter emitter) {
        List<SseEmitter> deviceEmitters = emitters.get(deviceId);
        if (deviceEmitters != null) {
            deviceEmitters.remove(emitter);
            log.info("[SSE] {} 기기 구독 종료 (남은 구독자: {}명)", deviceId, deviceEmitters.size());
        }
    }
}
