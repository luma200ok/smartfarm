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

    // ── 브라우저(대시보드) 구독자 ──────────────────────────────────────────────
    // deviceId → 연결된 emitter 목록 (다중 브라우저 탭 지원)
    private final Map<String, List<SseEmitter>> dashboardEmitters = new ConcurrentHashMap<>();
    private static final long DASHBOARD_TIMEOUT_MS = 30 * 60 * 1000L; // 30분

    // ── PC 클라이언트(명령 수신) 구독자 ──────────────────────────────────────
    // deviceId → PC 클라이언트 emitter (기기당 1개. 재연결 시 기존 연결 교체)
    private final Map<String, SseEmitter> deviceCommandEmitters = new ConcurrentHashMap<>();
    private static final long DEVICE_TIMEOUT_MS = 24 * 60 * 60 * 1000L; // 24시간

    // ─────────────────────────────────────────────────────────────────────────
    // 브라우저 구독
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 대시보드(브라우저)가 특정 기기를 구독할 때 호출됩니다.
     */
    public SseEmitter subscribe(String deviceId) {
        SseEmitter emitter = new SseEmitter(DASHBOARD_TIMEOUT_MS);

        dashboardEmitters.computeIfAbsent(deviceId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.info("[SSE-Dashboard] {} 기기 구독 시작 (현재 구독자: {}명)", deviceId, dashboardEmitters.get(deviceId).size());

        Runnable cleanup = () -> removeDashboardEmitter(deviceId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            removeDashboardEmitter(deviceId, emitter);
        }

        return emitter;
    }

    /**
     * 특정 기기를 구독 중인 모든 대시보드에 센서 데이터를 전송합니다.
     */
    public void sendToDevice(String deviceId, Object data) {
        List<SseEmitter> emitters = dashboardEmitters.get(deviceId);
        if (emitters == null || emitters.isEmpty()) return;

        emitters.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("sensor").data(data));
                return false;
            } catch (IOException e) {
                log.debug("[SSE-Dashboard] {} 기기 emitter 전송 실패, 제거합니다.", deviceId);
                return true;
            }
        });
    }

    private void removeDashboardEmitter(String deviceId, SseEmitter emitter) {
        List<SseEmitter> emitters = dashboardEmitters.get(deviceId);
        if (emitters != null) {
            emitters.remove(emitter);
            log.info("[SSE-Dashboard] {} 기기 구독 종료 (남은 구독자: {}명)", deviceId, emitters.size());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PC 클라이언트 명령 스트림
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * PC 클라이언트가 제어 명령 스트림을 구독할 때 호출됩니다.
     * 기기당 연결은 1개만 유지하며, 재연결 시 기존 연결을 graceful하게 교체합니다.
     */
    public SseEmitter subscribeDevice(String deviceId) {
        // 기존 연결이 있으면 complete() 로 정리
        SseEmitter existing = deviceCommandEmitters.get(deviceId);
        if (existing != null) {
            existing.complete();
        }

        SseEmitter emitter = new SseEmitter(DEVICE_TIMEOUT_MS);
        deviceCommandEmitters.put(deviceId, emitter);
        log.info("[SSE-Device] {} PC 클라이언트 명령 스트림 연결됨", deviceId);

        Runnable cleanup = () -> {
            deviceCommandEmitters.remove(deviceId, emitter);
            log.info("[SSE-Device] {} PC 클라이언트 명령 스트림 종료", deviceId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            deviceCommandEmitters.remove(deviceId, emitter);
        }

        return emitter;
    }

    /**
     * PC 클라이언트에 제어 명령을 즉시 푸시합니다.
     *
     * @return true  — SSE로 즉시 전달 성공
     *         false — PC 미연결이거나 전송 중 오류 (명령은 DB PENDING 상태로 유지)
     */
    public boolean sendCommandToDevice(String deviceId, Object data) {
        SseEmitter emitter = deviceCommandEmitters.get(deviceId);
        if (emitter == null) {
            log.info("[SSE-Device] {} PC 클라이언트 미연결 — 명령은 DB PENDING 유지됩니다.", deviceId);
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name("command").data(data));
            log.info("[SSE-Device] {} 기기에 제어 명령 푸시 완료", deviceId);
            return true;
        } catch (IOException e) {
            log.warn("[SSE-Device] {} 기기 명령 푸시 실패, 연결 제거. 명령은 DB PENDING 유지됩니다.", deviceId);
            deviceCommandEmitters.remove(deviceId, emitter);
            return false;
        }
    }
}
