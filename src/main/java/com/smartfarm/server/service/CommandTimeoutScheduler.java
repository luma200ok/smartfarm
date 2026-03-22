package com.smartfarm.server.service;

import com.smartfarm.server.repository.DeviceControlCommandRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * PENDING 상태로 오래 방치된 제어 명령을 주기적으로 CANCELLED 처리합니다.
 *
 * <p>PC 클라이언트가 장기간 오프라인이거나 네트워크 장애가 발생했을 때
 * 오래된 명령이 계속 PENDING으로 누적되는 것을 방지합니다.</p>
 *
 * <p>스케줄: 5분마다 실행, 생성된 지 10분 이상 된 PENDING 명령 취소</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommandTimeoutScheduler {

    private static final int TIMEOUT_MINUTES = 10;

    private final DeviceControlCommandRepository commandRepository;

    @Transactional
    @Scheduled(fixedDelay = 5 * 60 * 1000L) // 5분마다
    public void cancelTimedOutCommands() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        int cancelled = commandRepository.cancelTimedOutPendingCommands(cutoff);
        if (cancelled > 0) {
            log.info("[Scheduler] 타임아웃 PENDING 명령 {}건 CANCELLED 처리 완료 (기준: {}분 초과)", cancelled, TIMEOUT_MINUTES);
        } else {
            log.debug("[Scheduler] 타임아웃 PENDING 명령 없음");
        }
    }
}
