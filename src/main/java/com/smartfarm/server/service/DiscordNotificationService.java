package com.smartfarm.server.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 디스코드 웹훅을 통해 알림 메시지를 발송하는 서비스입니다.
 *
 * <p>쿨다운 기능: 같은 기기 + 같은 알림 타입은 {@code cooldownSeconds} 내에
 * 중복 발송하지 않습니다. Redis TTL 키로 관리합니다.</p>
 * <p>Redis 키 형식: {@code alert:cooldown:{deviceId}:{alertType}}</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotificationService {

    @Value("${smartfarm.notification.discord-webhook-url}")
    private String webhookUrl;

    @Value("${smartfarm.notification.cooldown-seconds}")
    private long cooldownSeconds;

    private final StringRedisTemplate redisTemplate;

    // HTTP 요청을 보내기 위한 RestTemplate 객체 (간단한 동기식 요청에 적합)
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 쿨다운을 적용하여 알림을 발송합니다.
     * 쿨다운 기간 내 같은 기기/같은 알림 타입이 들어오면 발송을 건너뜁니다.
     *
     * @param deviceId  기기 ID
     * @param alertType 알림 종류 (예: "TEMP", "HUMIDITY")
     * @param message   전송할 텍스트 메시지
     */
    public void sendAlertIfNotCoolingDown(String deviceId, String alertType, String message) {
        String cooldownKey = String.format("alert:cooldown:%s:%s", deviceId, alertType);

        // setIfAbsent = Redis SET NX EX : 키가 없을 때만 저장 성공(true) → 발송
        // 키가 이미 있으면 false → 쿨다운 중이므로 스킵
        Boolean isFirstAlert = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));

        if (Boolean.TRUE.equals(isFirstAlert)) {
            sendMessage(message);
        } else {
            log.info("[알림 쿨다운] {} {} 알림 억제 ({}초 내 중복 발송 방지)", deviceId, alertType, cooldownSeconds);
        }
    }

    /**
     * 디스코드 채널로 메시지를 전송합니다.
     * @param message 전송할 텍스트 메시지
     */
    public void sendMessage(String message) {
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.error("디스코드 웹훅 URL이 설정되지 않아 알림을 보낼 수 없습니다.");
            return;
        }

        try {
            // 디스코드 웹훅 API 규격에 맞게 JSON 바디 생성
            // 포맷: { "content": "보낼 메시지" }
            Map<String, String> payload = new HashMap<>();
            payload.put("content", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(payload, headers);

            // POST 요청으로 웹훅 전송
            restTemplate.postForEntity(webhookUrl, requestEntity, String.class);
            log.info("디스코드 알림 발송 성공: {}", message);

        } catch (Exception e) {
            log.error("디스코드 알림 발송 실패: {}", e.getMessage());
        }
    }
}
