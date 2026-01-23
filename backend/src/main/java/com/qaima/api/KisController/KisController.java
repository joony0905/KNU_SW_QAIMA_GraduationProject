package com.qaima.api.KisController;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * KIS 전용 테스트/디버그용 컨트롤러
 * - WebClientConfig 에서 정의한 kisWebClient 만 사용
 * - 응답은 일단 String(raw JSON) 으로 받아서 구조 확인용
 * - 나중에 필요하면 DTO 로 치환
 */
@RestController
@RequestMapping("/api/v1/admin/kis")
@RequiredArgsConstructor
public class KisController {

    @Qualifier("kisWebClient")
    private final WebClient kisWebClient;

    /**
     * 국내 주식 현재가 (예시용)
     * 예: GET /api/v1/kis/price?symbol=005930
     *
     */
    @GetMapping("/price")
    public Mono<String> getPrice(
            @RequestParam(name = "stockCode") String stockCode
    ) {
        resolveStockCode(stockCode);
        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        // TODO: 여기 path 를 기존 KIS 시세 조회 path 로 교체
                        // 예: "/uapi/domestic-stock/v1/quotations/inquire-price"
                        .path("<<KIS-PRICE-PATH>>")
                        // TODO: 쿼리 파라미터도 기존 코드 기준으로 맞추기
                        // 예: .queryParam("FID_INPUT_ISCD", symbol)
                        .build()
                )
                .headers(headers -> {
                    // TODO: KIS 인증 헤더 (appkey, appsecret, authorization 등)
                    // 기존 KISClient 에서 세팅하던 거 그대로 옮기면 됩니다.
                    // headers.set("authorization", "Bearer " + accessToken);
                    // headers.set("appkey", appKey);
                    // headers.set("appsecret", appSecret);
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 국내 주식 일봉/과거 시세 (예시용)
     * 예: GET /api/v1/kis/ohlcv/daily?symbol=005930&count=60
     *
     * 마찬가지로 path / 쿼리파라미터는
     * 기존 KIS 과거시세 호출 코드랑 똑같이 쓰시면 됩니다.
     */
    @GetMapping("/ohlcv/daily")
    public Mono<String> getDailyOhlcv(
            @RequestParam(name = "stockCode") String stockCode,
            @RequestParam(name = "count", defaultValue = "60") int count
    ) {
        resolveStockCode(stockCode);
        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        // TODO: 여기 path 를 KIS 일봉/과거시세 조회 path 로 교체
                        // 예: "/uapi/domestic-stock/v1/quotations/inquire-daily-price"
                        .path("<<KIS-OHLCV-PATH>>")
                        // TODO: 실제로 쓰는 파라미터 이름/값 맞추기
                        // .queryParam("FID_INPUT_ISCD", symbol)
                        // .queryParam("FID_PERIOD_DIV_CODE", "D") // 일봉 등
                        // .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build()
                )
                .headers(headers -> {
                    // TODO: 위와 동일하게 인증 헤더 세팅
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 완전 프리한 raw 호출용 (디버그)
     * 예: GET /api/v1/kis/raw?path=/uapi/...&symbol=005930
     *
     * - path, queryParam 을 바꿔가면서 응답 형태 탐색할 때 사용
     */
    @GetMapping("/raw")
    public Mono<String> getRaw(
            @RequestParam String path,
            @RequestParam(name = "stockCode") String stockCode
    ) {
        resolveStockCode(stockCode);
        return kisWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(path)
                        // TODO: 여기서도 symbol 관련 파라미터 이름을 기존 코드 기준으로
                        .build()
                )
                .headers(headers -> {
                })
                .retrieve()
                .bodyToMono(String.class);
    }

    private String resolveStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        return stockCode;
    }
}
