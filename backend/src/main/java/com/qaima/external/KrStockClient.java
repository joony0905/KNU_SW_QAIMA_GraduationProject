package com.qaima.external;

import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class KrStockClient {

    private final WebClient webClient;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    private String cachedToken;

    public KrStockClient(@Qualifier("kisWebClient") WebClient webClient) {
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
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(KisResponseDto.class)
                .map(resp -> {
                    cachedToken = "Bearer " + resp.getAccessToken();
                    return cachedToken;
                });
    }

    /**
     * 한국 종목 실시간 시세 → StockDto
     */
    public Mono<StockDto> fetchStock(Stock stock) {
        String code = stock.getStockCode();

        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                        .queryParam("FID_INPUT_ISCD", code)
                                        .build()
                                )
                                .header("authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "VHKST03010100")
                                .retrieve()
                                .bodyToMono(KisStatResponseDto.class)
                )
                .flatMap(resp -> {
                    KisStatResponseDto.Output o = resp.getOutput();
                    if (o == null) {
                        return Mono.error(new IllegalStateException("KIS output 없음"));
                    }
                    return Mono.just(mapToStockDto(stock, o));
                });
    }

    /**
     * 디버그용 티커 메타 (컨트롤러 /debug/ticker-meta 에서 사용)
     * KIS 티커 메타 조회
     * KIS 응답 스키마 기반으로 DTO 분리
     */
    public Mono<KisTickerMetaDto> fetchTickerMeta(String symbol) {
        String cleanSymbol = symbol
                .replace(".XKRX", "")
                .replace(".XKOS", "");

        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/api/domestic-stock/v1/quotations/inquire-price")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                        .queryParam("FID_INPUT_ISCD", cleanSymbol)
                                        .build()
                                )
                                .header("authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "VHKST03010100")
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

                    KisTickerMetaDto.KisTickerMetaDtoBuilder data = KisTickerMetaDto.builder()
                            .symbol(symbol)
                            .name("KIS_" + cleanSymbol);

                    try {
                        if (o.getStck_prpr() != null && !o.getStck_prpr().isBlank()) {
                            data.price(parseBig(o.getStck_prpr()));
                        }
                        if (o.getPrdy_ctrt() != null && !o.getPrdy_ctrt().isBlank()) {
                            data.changeRate(parseBig(o.getPrdy_ctrt()));
                        }
                    } catch (NumberFormatException e) {
                        log.error("[KrStockClient] 가격/등락률 파싱 에러: {}", e.getMessage());
                        return Mono.empty();
                    }

                    return Mono.just(data.build());
                })
                .onErrorResume(e -> {
                    log.error("[KrStockClient] 한투 API 에러: {}", e.getMessage(), e);
                    return Mono.empty();
                });
    }

    // raw output용 혅재가 시세 조회
    public Mono<KisStatResponseDto.Output> fetchKisStatRaw(String stockCode) {
        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/api/domestic-stock/v1/quotations/inquire-price")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                        .queryParam("FID_INPUT_ISCD", stockCode)
                                        .build()
                                )
                                .header("authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "FHKST01010100")
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

    //------------------------------------------


    //stock, stockdto 매핑
    private StockDto mapToStockDto(Stock s, KisStatResponseDto.Output o) {
        BigDecimal price = parseBig(o.getStck_prpr());
        BigDecimal change = parseBig(o.getPrdy_ctrt());

        Long industryId = (s.getIndustry() != null)
                ? s.getIndustry().getIndustryId()
                : null;

        return StockDto.builder()
                .stockId(s.getStockId())
                .stockCode(s.getStockCode())
                .isin(s.getIsin())
                .companyName(s.getCompanyName())
                .exchangeId(s.getExchange().getExchangeId())
                .exchangeCode(s.getExchange().getCode())
                .assetType(s.getAssetType())
                .currency(s.getCurrency())
                .industryId(industryId)
                .price(price)
                .changeRate(change)
                .listedAt(s.getListedAt())
                .delistedAt(s.getDelistedAt())
                .build();
    }


    /**
     * KIS 캔들(OHLCV) 조회
     * - freq: QAIMA 도메인 Freq → KIS interval 코드 매핑
     * - from/to: 일단 날짜 기준(일봉)으로 사용하는 걸 기본으로 두고, 필요하면 시분까지 확장
     */
    public Mono<List<PriceOhlcvDto>> fetchCandles(
            String stockCode,
            String marketDivCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        String interval = toKisInterval(freq);

        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "J") // 추후 J에서 확장
                                        .queryParam("FID_INPUT_ISCD", stockCode)
                                        .queryParam("FID_PERIOD_DIV_CODE", interval) // "D","W","M"
                                        .queryParam("FID_INPUT_DATE_1", toKisDateString(from))
                                        .queryParam("FID_INPUT_DATE_2", toKisDateString(to))
                                        .queryParam("FID_ORG_ADJ_PRC", "0")
                                        .build()
                                )
                                .header("authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "FHKST03010100")
                                .header("custtype", "P")
                                .header("content-type", "application/json; charset=utf-8")
                                .retrieve()
                                // HTTP 4xx/5xx면 바로 에러로
                                .onStatus(
                                        status -> status.is4xxClientError() || status.is5xxServerError(),
                                        resp -> resp.bodyToMono(String.class)
                                                .flatMap(body -> {
                                                    log.error("[KrStockClient] KIS candles HTTP error. status={}, body={}",
                                                            resp.statusCode(), body);
                                                    return Mono.error(new IllegalStateException(
                                                            "KIS candles HTTP error: " + body
                                                    ));
                                                })
                                )
                                .bodyToMono(KisCandlesResponse.class)
                                // KIS 비즈니스 코드 체크 (rt_cd != "0" 또는 빈 값 → 에러 처리)
                                .flatMap(resp -> {
                                    log.info("[KIS CANDLES RAW] rt_cd={}, msg_cd={}, msg1={}, candles_size={}",
                                            resp.getRt_cd(),
                                            resp.getMsg_cd(),
                                            resp.getMsg1(),
                                            resp.getOutput2() == null ? null : resp.getOutput2().size()
                                    );

                                    // 데이터 못받아옴 -> rt_cd="", output2=null이면 에러
                                    if (resp.getRt_cd() == null ||
                                            resp.getRt_cd().isBlank() ||
                                            !"0".equals(resp.getRt_cd())) {

                                        String msg = String.format(
                                                "KIS candles API biz error. rt_cd=%s, msg_cd=%s, msg1=%s",
                                                resp.getRt_cd(), resp.getMsg_cd(), resp.getMsg1()
                                        );
                                        return Mono.error(new IllegalStateException(msg));
                                    }

                                    return Mono.just(resp);
                                })
                                .map(resp -> mapToPriceOhlcvDtoList(resp, freq))
                );
    }



    private String toKisInterval(Freq freq) {
        return switch (freq) {
            case ONE_D -> "D";
            case ONE_W -> "W";
            case ONE_M -> "M";
            case ONE_H -> "60M";   // 분봉용 엔드포인트 쓸때 분리
            default -> "D";
        };
    }


    private List<PriceOhlcvDto> mapToPriceOhlcvDtoList(KisCandlesResponse resp, Freq freq) {

        if (resp == null || resp.getOutput2() == null) {
            return List.of();
        }

        int rawCount = resp.getOutput2().size();
        List<PriceOhlcvDto> candles = resp.getOutput2().stream()
                .map(candle -> toPriceOhlcvDto(candle, freq))
                .flatMap(Optional::stream)
                .toList();

        if (rawCount > 0 && candles.isEmpty()) {
            log.warn("KIS candles dropped entirely. rawCount={}", rawCount);
        }

        return candles;
    }

    // 내부 응답 DTO
    @Getter
    @Setter
    public static class KisCandlesResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private Output1 output1;
        private List<Candle> output2;

        @Getter @Setter
        public static class Output1 {
            private String stck_cntg_hour;
            private String stck_prpr;
            private String stck_oprc;
            private String stck_hgpr;
            private String stck_lwpr;
        }

        @Getter @Setter
        public static class Candle {
            private String stck_bsop_date; // 날짜 yyyyMMdd
            private String stck_oprc;      // 시가
            private String stck_hgpr;      // 고가
            private String stck_lwpr;      // 저가
            private String stck_clpr;      // 종가
            private String acml_vol;       // 거래량
        }
    }


    private Optional<PriceOhlcvDto> toPriceOhlcvDto(KisCandlesResponse.Candle candle, Freq freq) {
        OffsetDateTime ts = parseKisDate(candle.getStck_bsop_date());
        if (ts == null) {
            return Optional.empty();
        }

        BigDecimal open = parseBigOrNull(candle.getStck_oprc());
        BigDecimal high = parseBigOrNull(candle.getStck_hgpr());
        BigDecimal low = parseBigOrNull(candle.getStck_lwpr());
        BigDecimal close = parseBigOrNull(candle.getStck_clpr());

        if (open == null || high == null || low == null || close == null) {
            return Optional.empty();
        }

        return Optional.of(PriceOhlcvDto.builder()
                .ts(ts)
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(parseBigOrZero(candle.getAcml_vol()))
                .build());
    }

    private BigDecimal parseBig(String x) {
        try {
            if (x == null || x.isBlank()) return BigDecimal.ZERO;
            return new BigDecimal(x.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseBigOrNull(String x) {
        try {
            if (x == null || x.isBlank()) return null;
            return new BigDecimal(x.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigOrZero(String x) {
        return parseBig(x);
    }

    private OffsetDateTime parseKisDate(String yyyymmdd) {
        try {
            if (yyyymmdd == null || yyyymmdd.isBlank()) {
                return null;
            }
            LocalDate date = LocalDate.parse(yyyymmdd, DateTimeFormatter.ofPattern("yyyyMMdd"));
            return date.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private String toKisDateString(OffsetDateTime odt) {
        return odt.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private String toDate(LocalDate d) {
        return d != null ? d.toString() : null;
    }
}
