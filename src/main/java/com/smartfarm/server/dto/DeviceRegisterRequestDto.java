package com.smartfarm.server.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DeviceRegisterRequestDto {

    /** 기기 고유 ID. 영문 대문자, 숫자, 언더스코어만 허용 (예: WINDOWS_PC_01) */
    @NotBlank(message = "deviceId는 필수입니다.")
    @Size(min = 2, max = 50, message = "deviceId는 2~50자 이내여야 합니다.")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "deviceId는 영문 대문자, 숫자, 언더스코어(_)만 사용할 수 있습니다.")
    private String deviceId;
}
