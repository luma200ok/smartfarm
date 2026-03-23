package com.smartfarm.server.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 애플리케이션 공통 Bean 설정.
 */
@Configuration
public class AppConfig {

    /**
     * 외부 HTTP 호출에 사용할 RestTemplate.
     * 필드에서 직접 new RestTemplate() 하면 연결 풀이 없어 매 요청마다 소켓이 새로 생성됩니다.
     * Bean으로 등록하면 스프링 컨텍스트에서 싱글톤으로 관리되어 재사용됩니다.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
