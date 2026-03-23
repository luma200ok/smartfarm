package com.smartfarm.server.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserUpdateRequestDto {
    private String role;
    private String linkedDeviceId; // null이면 기기 연결 해제
}
