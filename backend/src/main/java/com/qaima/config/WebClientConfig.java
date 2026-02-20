package com.qaima.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
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
            @Value("${analysis.base-url}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        ObjectMapper analysisMapper = objectMapper.copy();
        // FastAPI 기능1 모델은 카멜 케이스 필드명을 사용
        analysisMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> {
                    configurer.defaultCodecs().jackson2JsonEncoder(
                            new Jackson2JsonEncoder(analysisMapper, MediaType.APPLICATION_JSON)
                    );
                    configurer.defaultCodecs().jackson2JsonDecoder(
                            new Jackson2JsonDecoder(analysisMapper, MediaType.APPLICATION_JSON)
                    );
                })
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .build();
    }

}
