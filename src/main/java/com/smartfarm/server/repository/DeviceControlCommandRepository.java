package com.smartfarm.server.repository;

import com.smartfarm.server.entity.CommandStatus;
import com.smartfarm.server.entity.DeviceControlCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceControlCommandRepository extends JpaRepository<DeviceControlCommand, Long> {

    /** 특정 기기의 PENDING 명령 목록 조회 (PC 클라이언트 폴링용) */
    List<DeviceControlCommand> findByDeviceIdAndStatusOrderByCreatedAtAsc(String deviceId, CommandStatus status);

    /** 특정 기기의 특정 명령 타입 중 PENDING 상태 건 조회 (중복 발송 방지 및 취소용) */
    List<DeviceControlCommand> findByDeviceIdAndCommandTypeAndStatus(String deviceId, String commandType, CommandStatus status);

    /** 특정 기기의 전체 명령 이력 (최신순 페이징) */
    Page<DeviceControlCommand> findByDeviceIdOrderByCreatedAtDesc(String deviceId, Pageable pageable);

    /** 특정 기기의 PENDING 명령 전체를 CANCELLED로 일괄 변경 */
    @Modifying
    @Query("UPDATE DeviceControlCommand c SET c.status = 'CANCELLED' WHERE c.deviceId = :deviceId AND c.status = 'PENDING'")
    int cancelAllPendingByDeviceId(String deviceId);

    /**
     * 지정 시각(cutoff) 이전에 생성된 PENDING 명령을 CANCELLED로 일괄 변경합니다.
     * CommandTimeoutScheduler 에서 주기적으로 호출합니다.
     *
     * @return 취소 처리된 명령 건수
     */
    @Modifying
    @Query("UPDATE DeviceControlCommand c SET c.status = 'CANCELLED' WHERE c.status = 'PENDING' AND c.createdAt < :cutoff")
    int cancelTimedOutPendingCommands(LocalDateTime cutoff);
}
