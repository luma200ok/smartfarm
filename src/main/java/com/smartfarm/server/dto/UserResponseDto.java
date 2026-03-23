package com.smartfarm.server.dto;

import com.smartfarm.server.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponseDto {

    private Long   id;
    private String username;
    private String role;
    private String linkedDeviceId;

    public static UserResponseDto from(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .linkedDeviceId(user.getLinkedDeviceId())
                .build();
    }
}
