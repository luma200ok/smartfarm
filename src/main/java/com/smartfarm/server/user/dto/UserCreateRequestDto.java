package com.smartfarm.server.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserCreateRequestDto {

    @NotBlank(message = "사용자명은 필수입니다.")
    private String username;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    private String role          = "ROLE_USER"; // 기본값: ROLE_USER
    private String linkedDeviceId;              // null = 기기 미연결
}
