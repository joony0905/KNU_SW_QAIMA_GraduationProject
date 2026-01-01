package com.qaima.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // 기타/공통 용도 (필요 시)
    @Bean(name = "defaultWebClient")
    public WebClient defaultWebClient() {
        return WebClient.builder().build();
    }

    // 한국투자증권(KIS) 전용
    @Bean(name = "kisWebClient")
    public WebClient kisWebClient(
            @Value("${kis.base-url}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // Marketstack 전용
    @Bean(name = "marketstackWebClient")
    public WebClient marketstackWebClient(
            @Value("${marketstack.base-url}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // FastAPI 분석 서버 전용
    @Bean(name = "analysisWebClient")
    public WebClient analysisWebClient(
            @Value("${analysis.base-url}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

}
