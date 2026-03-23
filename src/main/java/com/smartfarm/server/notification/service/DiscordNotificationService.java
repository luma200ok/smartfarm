package com.smartfarm.server.notification.service;

import lombok.RequiredArgsConstructor;
import com.smartfarm.server.device.service.DeviceConfigService;
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
 * <p>웹훅 URL 우선순위: 기기별 설정(DeviceConfig.discordWebhookUrl) → 전역 설정(application.yaml)</p>
 * <p>쿨다운 기능: 같은 기기 + 같은 알림 타입은 {@code cooldownSeconds} 내에
 * 중복 발송하지 않습니다. Redis TTL 키로 관리합니다.</p>
 * <p>Redis 키 형식: {@code alert:cooldown:{deviceId}:{alertType}}</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiscordNotificationService {

    @Value("${smartfarm.notification.discord-webhook-url}")
    private String globalWebhookUrl;

    @Value("${smartfarm.notification.cooldown-seconds}")
    private long cooldownSeconds;

    private final StringRedisTemplate redisTemplate;
    private final RestTemplate restTemplate;
    private final DeviceConfigService deviceConfigService;

    /**
     * 쿨다운을 적용하여 알림을 발송합니다.
     * 기기별 웹훅 URL이 설정되어 있으면 해당 채널로, 없으면 전역 채널로 전송합니다.
     *
     * @param deviceId  기기 ID
     * @param alertType 알림 종류 (예: "TEMP", "MEM_USAGE")
     * @param message   전송할 텍스트 메시지
     */
    public void sendAlertIfNotCoolingDown(String deviceId, String alertType, String message) {
        String cooldownKey = String.format("alert:cooldown:%s:%s", deviceId, alertType);

        Boolean isFirstAlert = redisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, "1", Duration.ofSeconds(cooldownSeconds));

        if (Boolean.TRUE.equals(isFirstAlert)) {
            String webhookUrl = resolveWebhookUrl(deviceId);
            sendMessageTo(webhookUrl, message);
        } else {
            log.info("[알림 쿨다운] {} {} 알림 억제 ({}초 내 중복 발송 방지)", deviceId, alertType, cooldownSeconds);
        }
    }

    /**
     * 전역 채널로 메시지를 전송합니다. (일간 리포트 등 기기와 무관한 알림에 사용)
     */
    public void sendMessage(String message) {
        sendMessageTo(globalWebhookUrl, message);
    }

    /**
     * 기기별 웹훅 URL을 조회합니다. 없으면 전역 URL을 반환합니다.
     */
    private String resolveWebhookUrl(String deviceId) {
        try {
            String deviceUrl = deviceConfigService.getDeviceConfig(deviceId).discordWebhookUrl();
            if (deviceUrl != null && !deviceUrl.isBlank()) {
                log.debug("[Discord] {} 기기별 웹훅 URL 사용", deviceId);
                return deviceUrl;
            }
        } catch (Exception e) {
            log.warn("[Discord] {} 기기 설정 조회 실패, 전역 URL로 폴백합니다: {}", deviceId, e.getMessage());
        }
        log.debug("[Discord] {} 전역 웹훅 URL 사용", deviceId);
        return globalWebhookUrl;
    }

    /**
     * 지정된 웹훅 URL로 메시지를 전송합니다.
     */
    private void sendMessageTo(String webhookUrl, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            log.error("디스코드 웹훅 URL이 설정되지 않아 알림을 보낼 수 없습니다.");
            return;
        }

        try {
            Map<String, String> payload = new HashMap<>();
            payload.put("content", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(payload, headers);
            restTemplate.postForEntity(webhookUrl, requestEntity, String.class);
            log.info("디스코드 알림 발송 성공: {}", message);

        } catch (Exception e) {
            log.error("디스코드 알림 발송 실패: {}", e.getMessage());
        }
    }
}
