package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.KisResponseDto;
import com.qaima.dto.KisStatResponseDto;
import com.qaima.dto.KisTickerMetaDto;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.dto.StockDto;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
public class KrStockClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public KrStockClient(
            @Qualifier("kisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    private volatile String cachedToken; // 간단 캐시(운영은 TTL 권장)

    /* =========================
       Token
       ========================= */

    private Mono<String> getAccessToken() {
        if (cachedToken != null) return Mono.just(cachedToken);

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
                })
                .onErrorMap(e -> new ErrorException(
                        ErrorCode.KIS_HTTP_ERROR,
                        "KIS token error: " + safeMsg(e.getMessage())
                ));
    }

    /* =========================
       1) 종목 시세 -> StockDto (KR)
       ========================= */

    public Mono<StockDto> fetchStock(Stock stock) {
        String code = stock.getStockCode();
        // 국내는 보통 "J"(주식). 필요하면 exchange 기반으로 변환해서 넣어도 됨.
        return fetchKisStatRaw(code, "J")
                .map(o -> mapToStockDto(stock, o));
    }

    /* =========================
       2) 디버그 티커 메타 (KIS inquire-price 재사용)
       ========================= */

    public Mono<KisTickerMetaDto> fetchTickerMeta(String symbol) {
        String cleanSymbol = symbol.replace(".XKRX", "").replace(".XKOS", "");

        return fetchKisStatRaw(cleanSymbol, "J")
                .map(o -> {
                    KisTickerMetaDto.KisTickerMetaDtoBuilder b = KisTickerMetaDto.builder()
                            .symbol(symbol)
                            .name("KIS_" + cleanSymbol);

                    if (o.getStck_prpr() != null && !o.getStck_prpr().isBlank()) {
                        b.price(parseBig(o.getStck_prpr()));
                    }
                    if (o.getPrdy_ctrt() != null && !o.getPrdy_ctrt().isBlank()) {
                        b.changeRate(parseBig(o.getPrdy_ctrt()));
                    }
                    return b.build();
                });
    }

    /* =========================
       3) raw output용 현재가 조회 (KIS)
       - overloading 제공 (기존 코드 호환)
       ========================= */

    // 기존 코드 호환: marketDivCode 없이 호출하면 "J"
    public Mono<KisStatResponseDto.Output> fetchKisStatRaw(String stockCode) {
        return fetchKisStatRaw(stockCode, "J");
    }

    public Mono<KisStatResponseDto.Output> fetchKisStatRaw(String stockCode, String marketDivCode) {
        final String endpoint = "inquire-price";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                                    .queryParam("FID_COND_MRKT_DIV_CODE", marketDivCode)
                                    .queryParam("FID_INPUT_ISCD", stockCode)
                                    .build()
                            )
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", "FHKST01010100");

                    return exchangeAndParse(spec, endpoint, KisStatResponseDto.class);
                })
                .map(raw -> {
                    KisStatResponseDto parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());

                    if (parsed.getOutput() == null) {
                        throw new ErrorException(
                                ErrorCode.KIS_BIZ_ERROR,
                                "KIS output is null. endpoint=" + endpoint + " body=" + truncate(raw.rawBody(), 800)
                        );
                    }
                    return parsed.getOutput();
                });
    }

    // 코드 확장시 사용
    public enum KisMarketDivCode {
        KRX("J"), ETF("Q"), ETN("U"), KONEX("K");

        private final String code;

        KisMarketDivCode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }


    /* =========================
       4) 캔들(OHLCV) 조회 (KIS)
       ========================= */

    public Mono<List<PriceOhlcvDto>> fetchCandles(
            String stockCode,
            String marketDivCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        final String endpoint = "inquire-daily-itemchartprice";
        final String interval = toKisInterval(freq);
        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                                    .queryParam("FID_COND_MRKT_DIV_CODE", "J") //나중에 marketDivcode로 변환
                                    .queryParam("FID_INPUT_ISCD", stockCode)
                                    .queryParam("FID_PERIOD_DIV_CODE", interval) // D/W/M
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
                            .header("content-type", "application/json; charset=utf-8");

                    return exchangeAndParse(spec, endpoint, KisCandlesResponse.class);
                })
                .map(raw -> {
                    KisCandlesResponse parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    return mapToPriceOhlcvDtoList(parsed, freq);
                });
    }

    /* =========================
       [표준 템플릿] status + headers + raw body + rt_cd 분기
       ========================= */

    private <T> Mono<KisRaw<T>> exchangeAndParse(
            WebClient.RequestHeadersSpec<?> spec,
            String endpointName,
            Class<T> clazz
    ) {
        return spec.exchangeToMono(resp -> readAndHandle(resp, endpointName, clazz));
    }

    private <T> Mono<KisRaw<T>> readAndHandle(ClientResponse resp, String endpointName, Class<T> clazz) {
        HttpStatusCode status = resp.statusCode();
        HttpHeaders headers = resp.headers().asHttpHeaders();

        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(raw -> {
                    log.debug("[KIS RAW] endpoint={} status={} headers={} body={}",
                            endpointName, status, safeHeaders(headers), truncate(raw, 4000));

                    if (!status.is2xxSuccessful()) {
                        throw new ErrorException(
                                ErrorCode.KIS_HTTP_ERROR,
                                "KIS HTTP error. endpoint=" + endpointName
                                        + " status=" + status
                                        + " body=" + truncate(raw, 800)
                        );
                    }

                    final T parsed;
                    try {
                        parsed = objectMapper.readValue(raw, clazz);
                    } catch (Exception ex) {
                        throw new ErrorException(
                                ErrorCode.KIS_DECODE_ERROR,
                                "KIS decode error. endpoint=" + endpointName
                                        + " body=" + truncate(raw, 800)
                        );
                    }

                    return Mono.just(new KisRaw<>(parsed, raw, headers, status));
                });
    }

    /** 200이어도 rt_cd로 실패 */
    private void requireRtOk(KisRtHeader parsed, String endpointName, String rawBody) {
        String rt = parsed.getRt_cd();
        if (rt == null || rt.isBlank() || !"0".equals(rt)) {
            String msg1 = parsed.getMsg1();

            // 시장 운영시간/휴장 감지 (패턴은 운영 msg1 보고 더 정밀화 가능)
            if (msg1 != null && (msg1.contains("장") && msg1.contains("시간"))) {
                throw new ErrorException(
                        ErrorCode.KIS_MARKET_CLOSED,
                        "KIS market closed. endpoint=" + endpointName
                                + " msg_cd=" + parsed.getMsg_cd()
                                + " msg1=" + msg1
                );
            }

            throw new ErrorException(
                    ErrorCode.KIS_BIZ_ERROR,
                    "KIS biz error. endpoint=" + endpointName
                            + " rt_cd=" + rt
                            + " msg_cd=" + parsed.getMsg_cd()
                            + " msg1=" + parsed.getMsg1()
                            + " body=" + truncate(rawBody, 800)
            );
        }
    }

    /* =========================
       Candles Response DTO (KisRtHeader implements)
       - “니가 제시한 코드에 선언이 없었다” 이 부분 해결
       ========================= */

    @Getter
    @Setter
    public static class KisCandlesResponse implements KisRtHeader {
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
            private String stck_bsop_date; // yyyyMMdd
            private String stck_oprc;
            private String stck_hgpr;
            private String stck_lwpr;
            private String stck_clpr;
            private String acml_vol;
        }
    }

    /* =========================
       Mapping / Helpers
       ========================= */

    private StockDto mapToStockDto(Stock s, KisStatResponseDto.Output o) {
        BigDecimal price = parseBig(o.getStck_prpr());
        BigDecimal changeRate = parseBig(o.getPrdy_ctrt());

        Long industryId = (s.getIndustry() != null) ? s.getIndustry().getIndustryId() : null;

        return StockDto.builder()
                .stockId(s.getStockId())
                .stockCode(s.getStockCode())
                .isin(s.getIsin())
                .companyName(s.getCompanyName())
                .exchangeId(s.getExchange() != null ? s.getExchange().getExchangeId() : null)
                .exchangeCode(s.getExchange() != null ? s.getExchange().getCode() : null)
                .assetType(s.getAssetType())
                .currency(s.getCurrency())
                .industryId(industryId)
                .price(price)
                .changeRate(changeRate)
                .listedAt(s.getListedAt())
                .delistedAt(s.getDelistedAt())
                .build();
    }

    private String toKisInterval(Freq freq) {
        return switch (freq) {
            case ONE_D -> "D";
            case ONE_W -> "W";
            case ONE_M -> "M";
            case ONE_H -> "60M";  // 분봉 엔드포인트로 분리 시 재설계
            default -> "D";
        };
    }

    private List<PriceOhlcvDto> mapToPriceOhlcvDtoList(KisCandlesResponse resp, Freq freq) {
        if (resp == null || resp.getOutput2() == null) return List.of();

        int rawCount = resp.getOutput2().size();
        List<PriceOhlcvDto> out = resp.getOutput2().stream()
                .map(c -> toPriceOhlcvDto(c, freq))
                .flatMap(Optional::stream)
                .toList();

        if (rawCount > 0 && out.isEmpty()) {
            log.warn("KIS candles dropped entirely. rawCount={}", rawCount);
        }
        return out;
    }

    private Optional<PriceOhlcvDto> toPriceOhlcvDto(KisCandlesResponse.Candle candle, Freq freq) {
        OffsetDateTime ts = parseKisDate(candle.getStck_bsop_date());
        if (ts == null) return Optional.empty();

        BigDecimal open = parseBigOrNull(candle.getStck_oprc());
        BigDecimal high = parseBigOrNull(candle.getStck_hgpr());
        BigDecimal low = parseBigOrNull(candle.getStck_lwpr());
        BigDecimal close = parseBigOrNull(candle.getStck_clpr());
        if (open == null || high == null || low == null || close == null) return Optional.empty();

        return Optional.of(PriceOhlcvDto.builder()
                .ts(ts)
                .freq(freq) // 저장 안정성 위해 freq 채움
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(parseBigOrZero(candle.getAcml_vol()))
                .build());
    }

    private OffsetDateTime parseKisDate(String yyyymmdd) {
        try {
            if (yyyymmdd == null || yyyymmdd.isBlank()) return null;
            LocalDate date = LocalDate.parse(yyyymmdd, DateTimeFormatter.ofPattern("yyyyMMdd"));
            return date.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private String toKisDateString(OffsetDateTime odt) {
        return odt.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
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

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated)";
    }

    private String safeHeaders(HttpHeaders h) {
        HttpHeaders copy = new HttpHeaders();
        h.forEach((k, v) -> {
            String key = k.toLowerCase();
            if (key.contains("authorization") || key.contains("appsecret") || key.contains("appkey")) return;
            copy.put(k, v);
        });
        return copy.toString();
    }

    private String safeMsg(String msg) {
        return (msg == null || msg.isBlank()) ? "n/a" : msg;
    }

    private record KisRaw<T>(T parsed, String rawBody, HttpHeaders headers, HttpStatusCode status) {}
}
