package com.qaima.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // 기본용 (다른 API 호출할 때 사용)
    @Bean(name = "defaultWebClient")
    public WebClient defaultWebClient() {
        return WebClient.builder().build();
    }

    // Marketstack 전용 WebClient
    @Bean(name = "marketstackClient")
    public WebClient marketstackClient(
            @Value("${marketstack.base-url}") String baseUrl)
    {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /*
    // 한국용: KIS (한국투자증권)
    @Bean(name = "kisClient")
    public WebClient kisClient(
            @Value("${kis.base-url}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }
    **/
}