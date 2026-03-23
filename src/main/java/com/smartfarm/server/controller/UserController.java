package com.smartfarm.server.controller;

import com.smartfarm.server.dto.UserCreateRequestDto;
import com.smartfarm.server.dto.UserResponseDto;
import com.smartfarm.server.dto.UserUpdateRequestDto;
import com.smartfarm.server.entity.User;
import com.smartfarm.server.exception.CustomException;
import com.smartfarm.server.exception.ErrorCode;
import com.smartfarm.server.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "4. 사용자 관리 API (Admin 전용)", description = "사용자 계정 생성/조회/수정/삭제 — 기기 접근 권한 관리")
public class UserController {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Operation(summary = "전체 사용자 목록 조회")
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> listUsers() {
        return ResponseEntity.ok(
                userRepository.findAll().stream()
                        .map(UserResponseDto::from)
                        .toList());
    }

    @Operation(summary = "사용자 생성", description = "role: ROLE_ADMIN 또는 ROLE_USER (기본값). linkedDeviceId: 접근 허용할 기기 ID (null이면 미연결)")
    @PostMapping
    public ResponseEntity<UserResponseDto> createUser(@Valid @RequestBody UserCreateRequestDto request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이미 존재하는 사용자명입니다.");
        }
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole() != null ? request.getRole() : "ROLE_USER")
                .linkedDeviceId(request.getLinkedDeviceId())
                .build();
        return ResponseEntity.ok(UserResponseDto.from(userRepository.save(user)));
    }

    @Operation(summary = "사용자 수정 (role / linkedDeviceId 변경)", description = "linkedDeviceId를 null로 보내면 기기 연결 해제")
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable Long id,
            @RequestBody UserUpdateRequestDto request) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 사용자입니다."));
        user.update(
                request.getRole() != null ? request.getRole() : user.getRole(),
                request.getLinkedDeviceId()
        );
        return ResponseEntity.ok(UserResponseDto.from(userRepository.save(user)));
    }

    @Operation(summary = "사용자 삭제")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        if (!userRepository.existsById(id)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "존재하지 않는 사용자입니다.");
        }
        userRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
