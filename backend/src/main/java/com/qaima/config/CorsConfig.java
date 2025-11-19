package com.qaima.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/api/**")   // 우리 API prefix에 맞게
                .allowedOrigins(
                        "http://localhost:5173"   // Vite 기본 포트
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)   // 나중에 쿠키/세션 쓸 가능성 고려
                .maxAge(3600);            // preflight 캐시 시간(초)
    }
}
