package com.qaima;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * QAIMA 백엔드 애플리케이션 진입점.
 * 현재는 단일 모듈 구조, 8080 포트로 기동.
 */
@SpringBootApplication

public class QaimaApplication {
    public static void main(String[] args) {
        // 스프링 부트 애플리케이션 실행
        SpringApplication.run(QaimaApplication.class, args);
    }
}
