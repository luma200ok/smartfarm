package com.smartfarm.server.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ROLE_ADMIN, ROLE_USER

    @Column(nullable = true)
    private String linkedDeviceId; // null = 전체 기기 접근 (admin), not-null = 해당 기기만 접근 (일반 사용자)

    @Builder
    public User(String username, String password, String role, String linkedDeviceId) {
        this.username       = username;
        this.password       = password;
        this.role           = role;
        this.linkedDeviceId = linkedDeviceId;
    }

    public void update(String role, String linkedDeviceId) {
        this.role           = role;
        this.linkedDeviceId = linkedDeviceId;
    }
}
