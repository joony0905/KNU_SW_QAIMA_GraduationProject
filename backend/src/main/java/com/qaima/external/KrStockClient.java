package com.qaima.external;

import com.qaima.dto.KisResponseDto;
import com.qaima.dto.KisStatResponseDto;
import com.qaima.dto.MarketStackTickersResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component

public class KrStockClient {

    private final WebClient webClient;

    @Value("${kis.base-url}")
    private String baseUrl;
    @Value("${kis.app-key}")
    private String appKey;
    @Value("${kis.app-secret}")
    private String appSecret;

    private String cachedToken;

    public KrStockClient(@Qualifier("defaultWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    private Mono<String> getAccessToken() {
        if (cachedToken != null) {
            return Mono.just(cachedToken);
        }

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        body.put("appkey", appKey);
        body.put("appsecret", appSecret);

        return webClient.post()
                .uri(baseUrl + "/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisResponseDto.class)
                .map(response -> {
                    this.cachedToken = "Bearer " + response.getAccessToken();
                    return this.cachedToken;
                });
    }

    public Mono<MarketStackTickersResponse.TickerData> fetchTickerMeta(String symbol) {
        String cleanSymbol = symbol.replace(".XKRX", "").replace(".XKOS", "");

        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .scheme("https")
                                        .host("openapi.koreainvestment.com")
                                        .port(9443)
                                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                        .queryParam("FID_INPUT_ISCD", cleanSymbol)
                                        .build())
                                .header("Authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "FHKST01010100")
                                .retrieve()
                                .bodyToMono(KisStatResponseDto.class)
                )
                .flatMap(resp -> {
                    if (!"0".equals(resp.getRt_cd())) {
                        log.warn("[KrStockClient] KIS rt_cd != 0, msg_cd={}, msg1={}",
                                resp.getMsg_cd(), resp.getMsg1());
                        return Mono.empty();
                    }

                    KisStatResponseDto.Output o = resp.getOutput();
                    if (o == null) {
                        log.warn("[KrStockClient] KIS output is null");
                        return Mono.empty();
                    }

                    MarketStackTickersResponse.TickerData data =
                            new MarketStackTickersResponse.TickerData();

                    data.setSymbol(symbol);
                    data.setName("KIS_" + cleanSymbol);

                    try {
                        if (o.getStck_prpr() != null && !o.getStck_prpr().isBlank()) {
                            data.setPrice(Double.parseDouble(o.getStck_prpr()));
                        }
                        if (o.getPrdy_ctrt() != null && !o.getPrdy_ctrt().isBlank()) {
                            data.setChangeRate(Double.parseDouble(o.getPrdy_ctrt()));
                        }
                    } catch (NumberFormatException e) {
                        log.error("[KrStockClient] 가격/등락률 파싱 에러: {}", e.getMessage());
                        return Mono.empty();
                    }

                    log.info("[KrStockClient] KIS 가격 매핑 완료: symbol={}, price={}, changeRate={}",
                            symbol, data.getPrice(), data.getChangeRate());

                    return Mono.just(data);
                })
                .onErrorResume(e -> {
                    log.error("[KrStockClient] 한투 API 에러: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    public Mono<KisStatResponseDto.Output> fetchKisStatRaw(String stockCode) {
        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")  // 코스피, 코스닥 등 기존 코드와 동일하게
                                        .queryParam("FID_INPUT_ISCD", stockCode)    // 종목코드
                                        .build()
                                )
                                .header("authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "FHKST01010100") // 기존에 쓰던 tr_id 그대로
                                .retrieve()
                                .bodyToMono(KisStatResponseDto.class)
                                .flatMap(resp -> {
                                    if (resp.getOutput() == null) {
                                        return Mono.error(new IllegalStateException("KIS 응답에 output 없음"));
                                    }
                                    return Mono.just(resp.getOutput());
                                })
                );
    }


}
