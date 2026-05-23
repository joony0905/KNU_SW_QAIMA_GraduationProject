package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.dto.kis.KisInvestorDailyByMarketResponseDto;
import com.qaima.dto.kis.KisInvestorTradeByStockDailyResponseDto;
import com.qaima.dto.kis.KisResponseDto;
import com.qaima.service.batch.KisBatchRateLimiter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
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

@Slf4j
@Component
public class KisInvestorFlowClient {

    public static final String TR_ID = "FHPTJ04160001";
    public static final String MARKET_TR_ID = "FHPTJ04040000";
    public static final String SOURCE = "KIS";
    public static final String DEFAULT_MARKET_DIV_CODE = "J";
    public static final String DEFAULT_MARKET_REQUEST_DIV_CODE = "U";
    public static final String DEFAULT_KOSPI_MARKET_INDUSTRY_CODE = "0001";
    public static final String DEFAULT_KOSDAQ_MARKET_INDUSTRY_CODE = "1001";

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final KisBatchRateLimiter rateLimiter;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    private volatile String cachedToken;
    private Mono<String> tokenMono;

    public KisInvestorFlowClient(
            @Qualifier("kisWebClient") WebClient webClient,
            ObjectMapper objectMapper,
            KisBatchRateLimiter rateLimiter
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
    }

    public Mono<List<KisInvestorTradeByStockDailyResponseDto.Row>> fetchInvestorTradeByStockDaily(
            String stockCode,
            LocalDate baseDate
    ) {
        return fetchInvestorTradeByStockDaily(stockCode, baseDate, DEFAULT_MARKET_DIV_CODE);
    }

    public Mono<List<KisInvestorTradeByStockDailyResponseDto.Row>> fetchInvestorTradeByStockDaily(
            String stockCode,
            LocalDate baseDate,
            String marketDivCode
    ) {
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }
        if (baseDate == null) {
            return Mono.error(new IllegalArgumentException("baseDate is required"));
        }
        String normalizedCode = normalizeStockCode(stockCode);
        String normalizedMarketDivCode = marketDivCode == null || marketDivCode.isBlank()
                ? DEFAULT_MARKET_DIV_CODE
                : marketDivCode.trim();
        final String endpoint = "investor-trade-by-stock-daily";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/quotations/investor-trade-by-stock-daily")
                                    .queryParam("FID_COND_MRKT_DIV_CODE", normalizedMarketDivCode)
                                    .queryParam("FID_INPUT_ISCD", normalizedCode)
                                    .queryParam("FID_INPUT_DATE_1", baseDate.format(BASIC_DATE))
                                    .queryParam("FID_ORG_ADJ_PRC", "")
                                    .queryParam("FID_ETC_CLS_CODE", "1")
                                    .build())
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", TR_ID)
                            .header("custtype", "P")
                            .accept(MediaType.APPLICATION_JSON);

                    return rateLimiter.acquireMono()
                            .then(exchangeAndParse(spec, endpoint, KisInvestorTradeByStockDailyResponseDto.class));
                })
                .map(raw -> {
                    KisInvestorTradeByStockDailyResponseDto parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    List<KisInvestorTradeByStockDailyResponseDto.Row> rows = parsed.getOutput2();
                    return rows == null ? List.of() : rows;
                });
    }

    public Mono<List<KisInvestorDailyByMarketResponseDto.Row>> fetchInvestorDailyByMarket(
            String marketCode,
            String industryCode,
            LocalDate from,
            LocalDate to
    ) {
        if (marketCode == null || marketCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("marketCode is required"));
        }
        if (from == null || to == null) {
            return Mono.error(new IllegalArgumentException("from and to are required"));
        }
        if (from.isAfter(to)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }
        String normalizedMarketCode = marketCode.trim().toUpperCase();
        String normalizedIndustryCode = industryCode == null || industryCode.isBlank()
                ? defaultMarketIndustryCode(normalizedMarketCode)
                : industryCode.trim();
        final String endpoint = "inquire-investor-daily-by-market";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/quotations/inquire-investor-daily-by-market")
                                    .queryParam("FID_COND_MRKT_DIV_CODE", DEFAULT_MARKET_REQUEST_DIV_CODE)
                                    .queryParam("FID_INPUT_ISCD", normalizedIndustryCode)
                                    .queryParam("FID_INPUT_DATE_1", to.format(BASIC_DATE))
                                    .queryParam("FID_INPUT_ISCD_1", normalizedMarketCode)
                                    .queryParam("FID_INPUT_DATE_2", to.format(BASIC_DATE))
                                    .queryParam("FID_INPUT_ISCD_2", normalizedIndustryCode)
                                    .build())
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", MARKET_TR_ID)
                            .header("custtype", "P")
                            .accept(MediaType.APPLICATION_JSON);

                    return rateLimiter.acquireMono()
                            .then(exchangeAndParse(spec, endpoint, KisInvestorDailyByMarketResponseDto.class));
                })
                .map(raw -> {
                    KisInvestorDailyByMarketResponseDto parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    List<KisInvestorDailyByMarketResponseDto.Row> rows = parsed.getOutput();
                    return rows == null ? List.of() : rows;
                });
    }

    private synchronized Mono<String> getAccessToken() {
        if (cachedToken != null) {
            return Mono.just(cachedToken);
        }
        if (tokenMono != null) {
            return tokenMono;
        }

        tokenMono = rateLimiter.acquireMono()
                .then(webClient.post()
                        .uri("/oauth2/tokenP")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .bodyValue(Map.of(
                                "grant_type", "client_credentials",
                                "appkey", appKey,
                                "appsecret", appSecret
                        ))
                        .retrieve()
                        .bodyToMono(KisResponseDto.class))
                .map(resp -> {
                    cachedToken = "Bearer " + resp.getAccessToken();
                    return cachedToken;
                })
                .doFinally(signal -> tokenMono = null)
                .cache();
        return tokenMono;
    }

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
                        return Mono.error(new ErrorException(
                                ErrorCode.KIS_HTTP_ERROR,
                                "KIS HTTP error. endpoint=" + endpointName
                                        + " status=" + status
                                        + " body=" + truncate(raw, 800)
                        ));
                    }

                    final T parsed;
                    try {
                        parsed = objectMapper.readValue(raw, clazz);
                    } catch (Exception ex) {
                        return Mono.error(new ErrorException(
                                ErrorCode.KIS_DECODE_ERROR,
                                "KIS decode error. endpoint=" + endpointName
                                        + " body=" + truncate(raw, 800)
                        ));
                    }
                    return Mono.just(new KisRaw<>(parsed, raw, headers, status));
                });
    }

    private void requireRtOk(KisRtHeader parsed, String endpointName, String rawBody) {
        String rt = parsed == null ? null : parsed.getRt_cd();
        if (!"0".equals(rt)) {
            String msgCode = parsed == null ? null : parsed.getMsg_cd();
            String msg = parsed == null ? null : parsed.getMsg1();
            if ("OPSQ2001".equals(msgCode) || (msg != null && msg.contains("TIME LIMIT"))) {
                throw new ErrorException(
                        ErrorCode.KIS_MARKET_CLOSED,
                        "KIS investor flow time limited. endpoint=" + endpointName
                                + " msg_cd=" + msgCode
                                + " msg1=" + msg
                );
            }
            throw new ErrorException(
                    ErrorCode.KIS_BIZ_ERROR,
                    "KIS biz error. endpoint=" + endpointName
                            + " rt_cd=" + rt
                            + " msg_cd=" + msgCode
                            + " msg1=" + msg
                            + " body=" + truncate(rawBody, 800)
            );
        }
    }

    private String normalizeStockCode(String stockCode) {
        String trimmed = stockCode.trim().replace(".XKRX", "").replace(".XKOS", "");
        if (trimmed.matches("\\d+")) {
            return String.format("%06d", Integer.parseInt(trimmed));
        }
        return trimmed;
    }

    public static String defaultMarketIndustryCode(String marketCode) {
        if (marketCode != null && "KSQ".equalsIgnoreCase(marketCode.trim())) {
            return DEFAULT_KOSDAQ_MARKET_INDUSTRY_CODE;
        }
        return DEFAULT_KOSPI_MARKET_INDUSTRY_CODE;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...(truncated)";
    }

    private String safeHeaders(HttpHeaders headers) {
        HttpHeaders copy = new HttpHeaders();
        headers.forEach((key, values) -> {
            String lower = key.toLowerCase();
            if (lower.contains("authorization") || lower.contains("appsecret") || lower.contains("appkey")) {
                return;
            }
            copy.put(key, values);
        });
        return copy.toString();
    }

    private record KisRaw<T>(T parsed, String rawBody, HttpHeaders headers, HttpStatusCode status) {
    }
}
