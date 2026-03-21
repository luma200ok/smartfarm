package com.smartfarm.server.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 프론트엔드(Thymeleaf) HTML 화면을 연결해주는 컨트롤러입니다.
 */
@Controller
public class WebController {

    // 1. 루트 경로(/) 접속 시 로그인 페이지(login.html)를 보여줍니다.
    @GetMapping("/")
    public String loginPage() {
        return "login"; // templates/login.html 파일과 매핑됩니다.
    }

    // 2. 로그인 성공 후(임시) 대시보드 화면(/dashboard)을 보여줍니다.
    @GetMapping("/dashboard")
    public String dashboardPage() {
        return "dashboard"; // templates/dashboard.html 파일과 매핑됩니다.
    }
}
