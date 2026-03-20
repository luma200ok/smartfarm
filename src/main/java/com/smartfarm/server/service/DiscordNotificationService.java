package com.smartfarm.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 디스코드 웹훅을 통해 알림 메시지를 발송하는 서비스입니다.
 */
@Slf4j
@Service
public class DiscordNotificationService {

    @Value("${smartfarm.notification.discord-webhook-url}")
    private String webhookUrl;

    // HTTP 요청을 보내기 위한 RestTemplate 객체 (간단한 동기식 요청에 적합)
    private final RestTemplate restTemplate = new RestTemplate();

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
