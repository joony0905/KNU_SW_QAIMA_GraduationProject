package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.Freq;
import com.qaima.service.feature2.IndustryIndexBar;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
public class IndustryIndexFetcher {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${kis.app-key}")
    private String appKey;

    @Value("${kis.app-secret}")
    private String appSecret;

    private volatile String cachedToken;
    private Mono<String> tokenMono;

    public IndustryIndexFetcher(
            @Qualifier("kisWebClient") WebClient webClient,
            ObjectMapper objectMapper
    ) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    /* =========================
       TOKEN (KrStockClient 동일)
       ========================= */
    private synchronized Mono<String> getAccessToken() {
        if (cachedToken != null) {
            log.debug("[IndustryIndexFetcher][TOKEN] use cached token");
            return Mono.just(cachedToken);
        }
        if (tokenMono != null) {
            log.debug("[IndustryIndexFetcher][TOKEN] join inflight token request");
            return tokenMono;
        }

        log.info("[IndustryIndexFetcher][TOKEN] requesting new access token");

        tokenMono = webClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(
                        java.util.Map.of(
                                "grant_type", "client_credentials",
                                "appkey", appKey,
                                "appsecret", appSecret
                        )
                )
                .retrieve()
                .bodyToMono(KisTokenResponse.class)
                .map(resp -> {
                    if (resp == null || resp.getAccess_token() == null || resp.getAccess_token().isBlank()) {
                        throw new ErrorException(ErrorCode.KIS_BIZ_ERROR, "KIS token response is empty");
                    }
                    cachedToken = "Bearer " + resp.getAccess_token();
                    log.info("[IndustryIndexFetcher][TOKEN] token issued successfully");
                    return cachedToken;
                })
                .doOnError(ex ->
                        log.error("[IndustryIndexFetcher][TOKEN] token issue failed: {}", ex.getMessage(), ex)
                )
                .doFinally(sig -> tokenMono = null)
                .cache();

        return tokenMono;
    }

    /* =========================
       MAIN API
       ========================= */

    public Mono<List<IndustryIndexBar>> fetch(
            String indexCode,
            Freq freq,
            LocalDate from,
            LocalDate to
    ) {
        String period = toKisPeriod(freq);
        String fromStr = format(from);
        String toStr = format(to);

        // 1자리 + 4자리 분리
        String marketDiv = indexCode.substring(0, 1);
        String iscd = indexCode.substring(1);

        log.info("[IndustryIndexFetcher][PARSED CODE] raw={}, marketDiv={}, iscd={}",
                indexCode, marketDiv, iscd);

        log.info("[IndustryIndexFetcher][KIS REQUEST] marketDiv={}, iscd={}, from={}, to={}, freq={}",
                marketDiv, iscd, fromStr, toStr, freq);

        return getAccessToken()
                .flatMap(token ->
                        webClient.get()
                                .uri(uriBuilder -> uriBuilder
                                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-indexchartprice")
                                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                                        .queryParam("FID_INPUT_ISCD", iscd)
                                        .queryParam("FID_INPUT_DATE_1", fromStr)
                                        .queryParam("FID_INPUT_DATE_2", toStr)
                                        .queryParam("FID_PERIOD_DIV_CODE", period)
                                        .build()
                                )
                                .header("authorization", token)
                                .header("appkey", appKey)
                                .header("appsecret", appSecret)
                                .header("tr_id", "FHKUP03500100")
                                .header("custtype", "P")
                                .accept(MediaType.APPLICATION_JSON)
                                .retrieve()
                                .bodyToMono(KisIndexResponse.class)
                )
                .doOnNext(resp -> logResponse(indexCode, freq, from, to, resp))
                .map(resp -> {
                    validateRt(resp);
                    return resp;
                })
                .map(resp -> {
                    List<IndustryIndexBar> bars = mapToBars(resp);
                    log.info("[IndustryIndexFetcher][MAPPED SIZE] indexCode={}, mappedSize={}",
                            indexCode, bars.size());
                    return bars;
                })
                .onErrorResume(ex -> {
                    log.error("[IndustryIndexFetcher][FAIL] indexCode={}, reason={}",
                            indexCode, ex.getMessage(), ex);
                    return Mono.just(List.of());
                });
    }

    /* =========================
       VALIDATION
       ========================= */

    private void validateRt(KisIndexResponse resp) {
        if (resp == null) {
            throw new ErrorException(ErrorCode.KIS_BIZ_ERROR, "KIS index response is null");
        }

        if (!"0".equals(resp.getRt_cd())) {
            throw new ErrorException(
                    ErrorCode.KIS_BIZ_ERROR,
                    "KIS index error: rt_cd=" + resp.getRt_cd()
                            + ", msg_cd=" + resp.getMsg_cd()
                            + ", msg1=" + resp.getMsg1()
            );
        }
    }

    /* =========================
       LOGGING
       ========================= */

    private void logResponse(
            String indexCode,
            Freq freq,
            LocalDate from,
            LocalDate to,
            KisIndexResponse resp
    ) {
        if (resp == null) {
            log.warn("[IndustryIndexFetcher][KIS RESPONSE] indexCode={}, from={}, to={}, freq={} -> response is null",
                    indexCode, format(from), format(to), freq);
            return;
        }

        log.info("[IndustryIndexFetcher][KIS RESPONSE] indexCode={}, rt_cd={}, msg_cd={}, msg1={}",
                indexCode, resp.getRt_cd(), resp.getMsg_cd(), resp.getMsg1());

        if (resp.getOutput2() == null) {
            log.warn("[IndustryIndexFetcher][KIS DATA SIZE] indexCode={}, output2 is null", indexCode);
        } else if (resp.getOutput2().isEmpty()) {
            log.warn("[IndustryIndexFetcher][KIS DATA SIZE] indexCode={}, output2 is empty", indexCode);
        } else {
            log.info("[IndustryIndexFetcher][KIS DATA SIZE] indexCode={}, output2 size={}",
                    indexCode, resp.getOutput2().size());

            KisIndexResponse.Row first = resp.getOutput2().get(0);
            KisIndexResponse.Row last = resp.getOutput2().get(resp.getOutput2().size() - 1);

            log.info("[IndustryIndexFetcher][KIS DATA RANGE] indexCode={}, firstDate={}, lastDate={}",
                    indexCode,
                    first != null ? first.getStck_bsop_date() : null,
                    last != null ? last.getStck_bsop_date() : null);
        }
    }

    /* =========================
       MAPPING
       ========================= */

    private List<IndustryIndexBar> mapToBars(KisIndexResponse resp) {
        if (resp == null) {
            log.warn("[IndustryIndexFetcher][MAP] response is null");
            return List.of();
        }

        if (resp.getOutput2() == null) {
            log.warn("[IndustryIndexFetcher][MAP] output2 is null");
            return List.of();
        }

        if (resp.getOutput2().isEmpty()) {
            log.warn("[IndustryIndexFetcher][MAP] output2 is empty");
            return List.of();
        }

        log.info("[IndustryIndexFetcher][MAP] raw output2 size={}", resp.getOutput2().size());

        List<IndustryIndexBar> result = resp.getOutput2().stream()
                .map(this::toBar)
                .filter(Objects::nonNull)
                .toList();

        log.info("[IndustryIndexFetcher][MAP] mapped result size={}", result.size());

        if (!resp.getOutput2().isEmpty() && result.isEmpty()) {
            log.warn("[IndustryIndexFetcher][MAP] output2 had data but mapped result is empty -> DTO/parsing issue suspected");
        }

        return result;
    }

    private IndustryIndexBar toBar(KisIndexResponse.Row r) {
        if (r == null) {
            log.warn("[IndustryIndexFetcher][ROW] row is null");
            return null;
        }

        try {
            OffsetDateTime ts = parseDate(r.getStck_bsop_date());

            return IndustryIndexBar.builder()
                    .ts(ts)
                    .open(parse(r.getBstp_nmix_oprc()))
                    .high(parse(r.getBstp_nmix_hgpr()))
                    .low(parse(r.getBstp_nmix_lwpr()))
                    .close(parse(r.getBstp_nmix_prpr()))
                    .volume(parse(r.getAcml_vol()))
                    .build();

        } catch (Exception e) {
            log.warn("[IndustryIndexFetcher][ROW PARSE FAIL] date={}, open={}, high={}, low={}, close={}, volume={}, reason={}",
                    r.getStck_bsop_date(),
                    r.getBstp_nmix_oprc(),
                    r.getBstp_nmix_hgpr(),
                    r.getBstp_nmix_lwpr(),
                    r.getBstp_nmix_prpr(),
                    r.getAcml_vol(),
                    e.getMessage(),
                    e);
            return null;
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private String toKisPeriod(Freq freq) {
        return switch (freq) {
            case ONE_D -> "D";
            case ONE_W -> "W";
            case ONE_M -> "M";
            default -> "D";
        };
    }

    private String format(LocalDate d) {
        return d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private OffsetDateTime parseDate(String yyyymmdd) {
        LocalDate d = LocalDate.parse(yyyymmdd, DateTimeFormatter.ofPattern("yyyyMMdd"));
        return d.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
    }

    private BigDecimal parse(String x) {
        try {
            if (x == null || x.isBlank()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(x.trim());
        } catch (Exception e) {
            log.warn("[IndustryIndexFetcher][NUMBER PARSE FAIL] raw={}", x);
            return BigDecimal.ZERO;
        }
    }

    /* =========================
       DTO
       ========================= */

    @Getter
    @Setter
    static class KisTokenResponse {
        private String access_token;
    }

    @Getter
    @Setter
    static class KisIndexResponse {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private List<Row> output2;

        @Getter
        @Setter
        static class Row {
            private String stck_bsop_date;

            private String bstp_nmix_oprc;
            private String bstp_nmix_hgpr;
            private String bstp_nmix_lwpr;
            private String bstp_nmix_prpr;

            private String acml_vol;
        }
    }
}