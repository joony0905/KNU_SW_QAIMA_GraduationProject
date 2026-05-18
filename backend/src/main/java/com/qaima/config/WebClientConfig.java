package com.qaima.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ClientCodecConfigurer;
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

    // 한국은행
    @Bean(name = "bokWebClient")
    public WebClient bokWebClient(
            @Value("${bok.base-url:https://ecos.bok.or.kr/api}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // FRED
    @Bean(name = "fredWebClient")
    public WebClient fredWebClient(
            @Value("${fred.base-url:https://api.stlouisfed.org}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    // SEC EDGAR
    @Bean(name = "secWebClient")
    public WebClient secWebClient(
            @Value("${sec.edgar.base-url:https://www.sec.gov}") String baseUrl,
            @Value("${sec.edgar.user-agent:qaima-dev contact@example.com}") String userAgent
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .codecs(this::configureSecCodecs)
                .build();
    }


    // opendart(금융감독원)
    @Bean(name = "finraWebClient")
    public WebClient finraWebClient(
            @Value("${finra.base-url:https://cdn.finra.org}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(this::configureLargeResponseCodecs)
                .build();
    }

    @Bean(name = "opendartWebClient")
    public WebClient openDartWebClient(
            @Value("${opendart.base-url:https://opendart.fss.or.kr/api}") String baseUrl
    ) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(this::configureOpenDartCodecs)
                .build();
    }

    private void configureOpenDartCodecs(ClientCodecConfigurer codecs) {
        configureLargeResponseCodecs(codecs);
    }

    private void configureLargeResponseCodecs(ClientCodecConfigurer codecs) {
        codecs.defaultCodecs().maxInMemorySize(16 * 1024 * 1024);
    }

    private void configureSecCodecs(ClientCodecConfigurer codecs) {
        codecs.defaultCodecs().maxInMemorySize(32 * 1024 * 1024);
    }

}
