package com.smartfarm.server.security;

import com.smartfarm.server.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security UserDetails 구현체.
 * linkedDeviceId를 담아 컨트롤러에서 @AuthenticationPrincipal로 바로 꺼낼 수 있습니다.
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long   id;
    private final String username;
    private final String password;
    private final String role;

    /**
     * 접근 가능한 기기 ID.
     * null이면 모든 기기 접근 가능 (ROLE_ADMIN).
     * not-null이면 해당 기기만 접근 가능 (ROLE_USER).
     */
    private final String linkedDeviceId;

    public UserPrincipal(User user) {
        this.id             = user.getId();
        this.username       = user.getUsername();
        this.password       = user.getPassword();
        this.role           = user.getRole();
        this.linkedDeviceId = user.getLinkedDeviceId();
    }

    /** ROLE_ADMIN 여부 */
    public boolean isAdmin() {
        return "ROLE_ADMIN".equals(role);
    }

    /**
     * 특정 deviceId에 접근 가능한지 검사.
     * admin이면 항상 true, 일반 사용자면 linkedDeviceId와 일치해야 true.
     */
    public boolean canAccess(String deviceId) {
        return isAdmin() || deviceId.equals(linkedDeviceId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
