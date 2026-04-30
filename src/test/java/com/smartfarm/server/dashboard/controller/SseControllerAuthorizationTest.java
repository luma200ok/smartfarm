package com.smartfarm.server.dashboard.controller;

import com.smartfarm.server.common.security.UserPrincipal;
import com.smartfarm.server.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SseControllerAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsDashboardSubscriptionForDifferentDevice() throws Exception {
        mockMvc.perform(get("/api/sse/subscribe")
                .param("deviceId", "OTHER-PC-001")
                .with(user(principal("ROLE_USER", "TEST-PC-001"))))
                .andExpect(status().isForbidden());
    }

    private UserPrincipal principal(String role, String linkedDeviceId) {
        return new UserPrincipal(User.builder()
                .username("viewer")
                .password("password")
                .role(role)
                .linkedDeviceId(linkedDeviceId)
                .build());
    }
}
