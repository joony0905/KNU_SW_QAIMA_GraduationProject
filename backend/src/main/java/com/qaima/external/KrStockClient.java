package com.qaima.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.Exchange;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.featuredstock.FeaturedStockDto;
import com.qaima.dto.featuredstock.FeaturedStockTopic;
import com.qaima.dto.kis.KisResponseDto;
import com.qaima.dto.kis.KisSearchInfoResponseDto;
import com.qaima.dto.kis.KisStatResponseDto;
import com.qaima.dto.kis.KisTickerMetaDto;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.StockDto;
import lombok.Getter;
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
import java.time.ZoneId;
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

    private volatile String cachedToken;

    // token single-flight
    private Mono<String> tokenMono;

    private synchronized Mono<String> getAccessToken() {
        if (cachedToken != null) return Mono.just(cachedToken);
        if (tokenMono != null) return tokenMono;

        tokenMono = webClient.post()
                .uri("/oauth2/tokenP")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "grant_type", "client_credentials",
                        "appkey", appKey,
                        "appsecret", appSecret
                ))
                .retrieve()
                .bodyToMono(KisResponseDto.class)
                .map(resp -> {
                    cachedToken = "Bearer " + resp.getAccessToken();
                    return cachedToken;
                })
                .doFinally(sig -> tokenMono = null)
                .cache();
        return tokenMono;
    }

    /* =========================
       1) 현재가 inquire-price
       ========================= */

    public Mono<StockDto> fetchStock(Stock stock) {
        String code = stock.getStockCode();
        String marketDivCode = toKisMarketDivCode(stock.getExchange()); // J/Q/K
        return fetchKisStatRaw(code, marketDivCode)
                .map(o -> mapToStockDto(stock, o));
    }

    /**
     * StockApiClient에서 "canonical + exchange"로 호출하고 싶을 때 쓰는 형태
     */
    public Mono<StockDto> fetchStockByCanonical(String canonicalStockCode, Exchange exchangeOrNull) {
        Stock stub = new Stock();
        stub.setStockCode(canonicalStockCode);
        stub.setExchange(exchangeOrNull);
        return fetchStock(stub);
    }

    public Mono<KisTickerMetaDto> fetchTickerMeta(String symbol) {
        String cleanSymbol = symbol.replace(".XKRX", "").replace(".XKOS", "");

        // ticker meta는 exchange 판단을 위해 inquire-price가 필요
        return fetchKisStatRaw(cleanSymbol, "J")
                .map(out -> {
                    KisTickerMetaDto.KisTickerMetaDtoBuilder b = KisTickerMetaDto.builder()
                            .stockCode(cleanSymbol)
                            .companyName(cleanSymbol) // fallback 유지 (StockApiClient에서 search-info prdt_name으로 덮어씀)
                            .exchangeCode(resolveDomesticExchangeCode(out))
                            .currency("KRW");

                    if (out != null) {
                        if (out.getStck_prpr() != null && !out.getStck_prpr().isBlank()) {
                            b.price(parseBig(out.getStck_prpr()));
                        }
                        if (out.getPrdy_ctrt() != null && !out.getPrdy_ctrt().isBlank()) {
                            b.changeRate(parseBig(out.getPrdy_ctrt()));
                        }
                    }
                    return b.build();
                });
    }

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
                            .header("tr_id", "FHKST01010100")
                            .accept(MediaType.APPLICATION_JSON);

                    return exchangeAndParse(spec, endpoint, KisStatResponseDto.class);
                })
                .handle((raw, sink) -> {
                    KisStatResponseDto parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());

                    if (parsed.getOutput() == null) {
                        sink.error(new ErrorException(
                                ErrorCode.KIS_BIZ_ERROR,
                                "KIS output is null. endpoint=" + endpoint + " body=" + truncate(raw.rawBody(), 800)
                        ));
                        return;
                    }
                    sink.next(parsed.getOutput());
                });
    }

    /* =========================
       2) search-info / search-stock-info (CTPF1002R)
       - '상품번호 필수'를 해결하기 위해 PDNO로 호출
       ========================= */

    public Mono<KisSearchInfoResponseDto.Output> fetchSearchInfoRaw(String stockCode, String marketDivCode) {
        final String endpoint = "search-stock-info";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/quotations/search-stock-info")
                                    .queryParam("FID_COND_MRKT_DIV_CODE", marketDivCode)
                                    .queryParam("PDNO", stockCode)
                                    .queryParam("PRDT_TYPE_CD", "300")
                                    .build()
                            )
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", "CTPF1002R")
                            .header("custtype", "P")
                            .accept(MediaType.APPLICATION_JSON);

                    return exchangeAndParse(spec, endpoint, KisSearchInfoResponseDto.class);

                })
                .handle((raw, sink) -> {
                    KisSearchInfoResponseDto parsed = raw.parsed();
                    log.info("[KIS {}] rt_cd={} msg_cd={} msg1={} body={}",
                            endpoint,
                            parsed != null ? parsed.getRt_cd() : null,
                            parsed != null ? parsed.getMsg_cd() : null,
                            parsed != null ? parsed.getMsg1() : null,
                            raw.rawBody()
                    );
                    requireRtOk(parsed, endpoint, raw.rawBody());

                    if (parsed.getOutput() == null) {
                        sink.error(new ErrorException(
                                ErrorCode.KIS_BIZ_ERROR,
                                "KIS output is null. endpoint=" + endpoint + " body=" + truncate(raw.rawBody(), 800)
                        ));
                        return;
                    }
                    sink.next(parsed.getOutput());
                });
    }

    /* =========================
       3) 캔들
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
                                    .queryParam("FID_COND_MRKT_DIV_CODE", marketDivCode)
                                    .queryParam("FID_INPUT_ISCD", stockCode)
                                    .queryParam("FID_PERIOD_DIV_CODE", interval)
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
                            .accept(MediaType.APPLICATION_JSON);

                    return exchangeAndParse(spec, endpoint, KisCandlesResponse.class);
                })
                .map(raw -> {
                    KisCandlesResponse parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    return mapToPriceOhlcvDtoList(parsed, freq);
                });
    }

    /* =========================
       4) 특징주/순위
       ========================= */

    public Mono<List<FeaturedStockDto>> fetchFeaturedStocks(FeaturedStockTopic topic, int limit) {
        return switch (topic) {
            case GAINERS -> fetchFluctuationRanking("0", limit);
            case LOSERS -> fetchFluctuationRanking("1", limit);
            case NEAR_NEW_HIGH -> fetchNearNewHighLowRanking("0", limit);
            case NEAR_NEW_LOW -> fetchNearNewHighLowRanking("1", limit);
            case TOP_TURNOVER -> fetchVolumeRanking("3", limit);
            case VOLUME_SURGE -> fetchVolumeRanking("1", limit);
        };
    }

    private Mono<List<FeaturedStockDto>> fetchVolumeRanking(String belongingClassCode, int limit) {
        final String endpoint = "volume-rank";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                                    .queryParam("FID_COND_SCR_DIV_CODE", "20171")
                                    .queryParam("FID_INPUT_ISCD", "0000")
                                    .queryParam("FID_DIV_CLS_CODE", "0")
                                    .queryParam("FID_BLNG_CLS_CODE", belongingClassCode)
                                    .queryParam("FID_TRGT_CLS_CODE", "111111111")
                                    .queryParam("FID_TRGT_EXLS_CLS_CODE", "0000000000")
                                    .queryParam("FID_INPUT_PRICE_1", "")
                                    .queryParam("FID_INPUT_PRICE_2", "")
                                    .queryParam("FID_VOL_CNT", "")
                                    .build()
                            )
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", "FHPST01710000")
                            .header("custtype", "P")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON);

                    return exchangeAndParse(spec, endpoint, KisVolumeRankResponse.class);
                })
                .map(raw -> {
                    KisVolumeRankResponse parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    if (parsed.getOutput() == null) return List.<FeaturedStockDto>of();
                    return parsed.getOutput().stream()
                            .limit(limit)
                            .map(this::toFeaturedStock)
                            .toList();
                });
    }

    private Mono<List<FeaturedStockDto>> fetchFluctuationRanking(String sortClassCode, int limit) {
        final String endpoint = "fluctuation";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/ranking/fluctuation")
                                    .queryParam("fid_rsfl_rate2", "")
                                    .queryParam("fid_cond_mrkt_div_code", "J")
                                    .queryParam("fid_cond_scr_div_code", "20170")
                                    .queryParam("fid_input_iscd", "0000")
                                    .queryParam("fid_rank_sort_cls_code", sortClassCode)
                                    .queryParam("fid_input_cnt_1", "0")
                                    .queryParam("fid_prc_cls_code", "1")
                                    .queryParam("fid_input_price_1", "")
                                    .queryParam("fid_input_price_2", "")
                                    .queryParam("fid_vol_cnt", "")
                                    .queryParam("fid_trgt_cls_code", "0")
                                    .queryParam("fid_trgt_exls_cls_code", "0")
                                    .queryParam("fid_div_cls_code", "0")
                                    .queryParam("fid_rsfl_rate1", "")
                                    .build()
                            )
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", "FHPST01700000")
                            .header("custtype", "P")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON);

                    return exchangeAndParse(spec, endpoint, KisFluctuationRankResponse.class);
                })
                .map(raw -> {
                    KisFluctuationRankResponse parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    if (parsed.getOutput() == null) return List.<FeaturedStockDto>of();
                    return parsed.getOutput().stream()
                            .limit(limit)
                            .map(this::toFeaturedStock)
                            .toList();
                });
    }

    private Mono<List<FeaturedStockDto>> fetchNearNewHighLowRanking(String priceClassCode, int limit) {
        final String endpoint = "near-new-highlow";

        return getAccessToken()
                .flatMap(token -> {
                    var spec = webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/uapi/domestic-stock/v1/ranking/near-new-highlow")
                                    .queryParam("fid_aply_rang_vol", "0")
                                    .queryParam("fid_cond_mrkt_div_code", "J")
                                    .queryParam("fid_cond_scr_div_code", "20187")
                                    .queryParam("fid_div_cls_code", "0")
                                    .queryParam("fid_input_cnt_1", "0")
                                    .queryParam("fid_input_cnt_2", "5")
                                    .queryParam("fid_prc_cls_code", priceClassCode)
                                    .queryParam("fid_input_iscd", "0000")
                                    .queryParam("fid_trgt_cls_code", "0")
                                    .queryParam("fid_trgt_exls_cls_code", "0")
                                    .queryParam("fid_aply_rang_prc_1", "")
                                    .queryParam("fid_aply_rang_prc_2", "")
                                    .build()
                            )
                            .header("authorization", token)
                            .header("appkey", appKey)
                            .header("appsecret", appSecret)
                            .header("tr_id", "FHPST01870000")
                            .header("custtype", "P")
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON);

                    return exchangeAndParse(spec, endpoint, KisNearNewHighLowResponse.class);
                })
                .map(raw -> {
                    KisNearNewHighLowResponse parsed = raw.parsed();
                    requireRtOk(parsed, endpoint, raw.rawBody());
                    if (parsed.getOutput() == null) return List.<FeaturedStockDto>of();
                    return parsed.getOutput().stream()
                            .limit(limit)
                            .map(this::toFeaturedStock)
                            .toList();
                });
    }

    /* =========================
       Template
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
        String rt = parsed.getRt_cd();
        if (rt == null || rt.isBlank() || !"0".equals(rt)) {
            String msg1 = parsed.getMsg1();
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
       Candles Response
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

    @Getter
    @Setter
    public static class KisVolumeRankResponse implements KisRtHeader {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private List<Row> output;

        @Getter @Setter
        public static class Row {
            private String hts_kor_isnm;
            private String mksc_shrn_iscd;
            private String stck_prpr;
            private String prdy_vrss_sign;
            private String prdy_vrss;
            private String prdy_ctrt;
            private String acml_vol;
        }
    }

    @Getter
    @Setter
    public static class KisFluctuationRankResponse implements KisRtHeader {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private List<Row> output;

        @Getter @Setter
        public static class Row {
            private String stck_shrn_iscd;
            private String hts_kor_isnm;
            private String stck_prpr;
            private String prdy_vrss_sign;
            private String prdy_vrss;
            private String prdy_ctrt;
            private String acml_vol;
        }
    }

    @Getter
    @Setter
    public static class KisNearNewHighLowResponse implements KisRtHeader {
        private String rt_cd;
        private String msg_cd;
        private String msg1;
        private List<Row> output;

        @Getter @Setter
        public static class Row {
            private String hts_kor_isnm;
            private String mksc_shrn_iscd;
            private String stck_prpr;
            private String prdy_vrss_sign;
            private String prdy_vrss;
            private String prdy_ctrt;
            private String acml_vol;
        }
    }

    /* =========================
       Mapping / Helpers
       ========================= */

    private FeaturedStockDto toFeaturedStock(KisVolumeRankResponse.Row row) {
        return new FeaturedStockDto(
                null,
                row.getMksc_shrn_iscd(),
                row.getHts_kor_isnm(),
                "KRX",
                parseBig(row.getStck_prpr()),
                parseBig(row.getAcml_vol()),
                signedByKisSign(row.getPrdy_vrss(), row.getPrdy_vrss_sign()),
                signedByKisSign(row.getPrdy_ctrt(), row.getPrdy_vrss_sign())
        );
    }

    private FeaturedStockDto toFeaturedStock(KisFluctuationRankResponse.Row row) {
        return new FeaturedStockDto(
                null,
                row.getStck_shrn_iscd(),
                row.getHts_kor_isnm(),
                "KRX",
                parseBig(row.getStck_prpr()),
                parseBig(row.getAcml_vol()),
                signedByKisSign(row.getPrdy_vrss(), row.getPrdy_vrss_sign()),
                signedByKisSign(row.getPrdy_ctrt(), row.getPrdy_vrss_sign())
        );
    }

    private FeaturedStockDto toFeaturedStock(KisNearNewHighLowResponse.Row row) {
        return new FeaturedStockDto(
                null,
                row.getMksc_shrn_iscd(),
                row.getHts_kor_isnm(),
                "KRX",
                parseBig(row.getStck_prpr()),
                parseBig(row.getAcml_vol()),
                signedByKisSign(row.getPrdy_vrss(), row.getPrdy_vrss_sign()),
                signedByKisSign(row.getPrdy_ctrt(), row.getPrdy_vrss_sign())
        );
    }

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
            case ONE_H -> "60M";
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
                .freq(freq)
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
            return date.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
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

    private BigDecimal signedByKisSign(String value, String signCode) {
        BigDecimal parsed = parseBig(value);
        if ("4".equals(signCode) || "5".equals(signCode)) {
            return parsed.abs().negate();
        }
        if ("3".equals(signCode)) {
            return BigDecimal.ZERO;
        }
        return parsed.abs();
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

    // Exchange 단일진실원: rprs_mrkt_kor_name
    private String resolveDomesticExchangeCode(KisStatResponseDto.Output out) {
        if (out == null) return "KRX";
        String name = out.getRprs_mrkt_kor_name();
        if (name == null || name.isBlank()) return "KRX";

        String n = name.trim();
        String u = n.toUpperCase(Locale.ROOT);

        if (u.contains("KOSPI")) return "KOSPI";
        if (u.contains("KOSDAQ")) return "KOSDAQ";
        if (u.contains("KONEX")) return "KONEX";

        if (n.contains("코스피") || n.contains("유가")) return "KOSPI";
        if (n.contains("코스닥")) return "KOSDAQ";
        if (n.contains("코넥스")) return "KONEX";

        return "KRX";
    }

    // DB/엔티티 exchange.code -> KIS market div code
    // KIS MarketDivCode가 KRX로 전체 통일돼서 J,K,Q에서 J로 변경함
    // 26.04.05 기준 사실상 필요없는 레거시 코드이나, scope가 나스닥까지 확장될경우를 대비해 혹시나해서 남겨둠
    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null) return "J";
        String code = exchange.getCode();
        if (code == null || code.isBlank()) return "J";

        return switch (code) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "J";
            case "KONEX" -> "J";
            case "KRX" -> "J";
            default -> "J";
        };
    }

    private String safeMsg(String msg) {
        return (msg == null || msg.isBlank()) ? "n/a" : msg;
    }

    private record KisRaw<T>(T parsed, String rawBody, HttpHeaders headers, HttpStatusCode status) {}
}
