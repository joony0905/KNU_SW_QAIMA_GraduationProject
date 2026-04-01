package com.qaima.service.featone;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.Exchange;
import com.qaima.domain.Financial;
import com.qaima.domain.Freq;
import com.qaima.domain.PeriodType;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.PriceOhlcvId;
import com.qaima.domain.Stock;
import com.qaima.dto.featone.FeatOneAnalysisExplainDto;
import com.qaima.dto.featone.FeatOneAnalysisMetricsDto;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import com.qaima.dto.financial.FinancialSummaryDto;
import com.qaima.dto.financial.FinancialSummaryMetricsDto;
import com.qaima.dto.ohlcv.OhlcvItemDto;
import com.qaima.dto.ohlcv.OhlcvSummaryDto;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.indicator.IndicatorBundleDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.GlobalStockClient;
import com.qaima.external.KrStockClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.service.stock.MarketSnapshotService;
import com.qaima.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.codec.DecodingException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatOneService {

    private static final Logger log = LoggerFactory.getLogger(FeatOneService.class);

    private static final int DEFAULT_FINANCIAL_LIMIT = 5;
    private static final String SCHEMA_VERSION = "0.1"; //network에서 0.1로 떨어지면 백엔드 오류

    private final StockService stockService;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final FinancialRepository financialRepository;
    private final MarketSnapshotService marketSnapshotService;

    // Feature1은 직접 KIS/Marketstack을 사용
    private final KrStockClient krStockClient;
    private final GlobalStockClient globalStockClient;
    private final AnalysisApiClient analysisApiClient;

    public Mono<FeatOneResult> getFeatOneData(
            String stockCode,
            Freq freq,
            String from, // Offset이 아닌 String으로 넘김. 분석 및 외부 출력용
            String to,
            String marketDivCode,
            Boolean includeExplain
    ) {
        if (stockCode == null || stockCode.isBlank()
                || freq == null
                || from == null
                || to == null
                || marketDivCode == null
                || includeExplain == null) {

            log.warn("[FeatOneService param validation fail] stockCode={}, freq={}, from={}, to={}, marketDivCode={}, includeExplain={}",
                    stockCode, freq, from, to, marketDivCode, includeExplain);
            throw new ErrorException(ErrorCode.VALIDATION_ERROR);
        }

        OffsetDateTime fromDt = parseRequestDateTime(from, false);
        OffsetDateTime toDt = parseRequestDateTime(to, true);
        if (toDt.isBefore(fromDt)) {
            throw new IllegalArgumentException("to must be same as or after from.");
        }

        Mono<Stock> stockMono = stockService.getOrCreateStockByCode(stockCode).cache();

        Mono<List<PriceOhlcv>> candlesMono = stockMono.flatMap(stock ->
                loadCandlesWithFallback(stock, freq, fromDt, toDt, marketDivCode)
        );

        Mono<List<Financial>> financialsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                financialRepository.findByStockOrderByReportDateDescVersionDesc(
                                        stock,
                                        PageRequest.of(0, DEFAULT_FINANCIAL_LIMIT)
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        Mono<Boolean> marketSnapshotMono = stockMono
                .flatMap(stock -> refreshMarketSnapshot(stock, marketDivCode))
                .defaultIfEmpty(Boolean.TRUE);

        return Mono.zip(stockMono, candlesMono, financialsMono, marketSnapshotMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    List<PriceOhlcv> candles = tuple.getT2();
                    List<Financial> financials = tuple.getT3();

                    FeatOneRequestDto requestDto =
                            buildFeatOneRequestDto(stock, freq, candles, financials, includeExplain);

                    log.info("[FeatOneService][analysis-request] stockCode={}, freq={}, ohlcvSize={}, financialsSize={}, includeExplain={}",
                            requestDto.getStockCode(),
                            requestDto.getFreq(),
                            requestDto.getOhlcv() != null ? requestDto.getOhlcv().size() : 0,
                            requestDto.getFinancials() != null ? requestDto.getFinancials().size() : 0,
                            requestDto.getIncludeExplain());

                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(response -> {
                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return new FeatOneResult(response, chartUnavailable);
                            })
                            .onErrorResume(ex -> {
                                // indicator가 사라지는 현상은 여기로 떨어져 fallback이 내려가면서 발생한다.
                                String apiWarn = toAnalysisApiWarn(ex);

                                log.error("[Feature1] Analysis API failed. stockCode={}, freq={}, from={}, to={}, includeExplain={}, warn={}",
                                        stockCode, freq, from, to, includeExplain, apiWarn, ex);

                                FeatOneAnalysisResponseDto fallback =
                                        buildFallbackResponse(stock, candles, financials, includeExplain, apiWarn);

                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return Mono.just(new FeatOneResult(fallback, chartUnavailable));
                            });
                });
    }

    private Mono<Boolean> refreshMarketSnapshot(Stock stock, String marketDivCodeOverride) {
        if (stock == null || stock.getExchange() == null) {
            return Mono.just(Boolean.TRUE);
        }

        LocalDate baseDate = LocalDate.now();
        return marketSnapshotService.getLatestDto(stock, baseDate)
                .thenReturn(Boolean.TRUE)
                .onErrorResume(ex -> {
                    log.warn("[FeatOneService] market snapshot read failed. stockCode={}",
                            stock.getStockCode(), ex);
                    return Mono.just(Boolean.TRUE);
                });
    }

    /**
     * 1) DB 조회
     * 2) 비어있으면 KIS 호출
     * 3) KIS가 http/biz/market_closed 실패면 Marketstack 폴백
     * 4) decode는 내부 버그로 간주 → 그대로 throw
     * 5) 외부 데이터는 saveAll 후 반환
     */
    private Mono<List<PriceOhlcv>> loadCandlesWithFallback(
            Stock stock,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to,
            String marketDivCodeOverride
    ) {
        String stockCode = stock.getStockCode();
        String marketDivCode = (marketDivCodeOverride != null && !marketDivCodeOverride.isBlank())
                ? marketDivCodeOverride
                : toKisMarketDivCode(stock.getExchange());

        return Mono.fromCallable(() -> priceOhlcvRepository.findRange(stockCode, freq, from, to))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> {
                    if (!existing.isEmpty()) {
                        return Mono.just(existing);
                    }

                    Mono<List<PriceOhlcvDto>> fromKis =
                            krStockClient.fetchCandles(stockCode, marketDivCode, freq, from, to);

                    Mono<List<PriceOhlcvDto>> fromGlobal =
                            fromKis.onErrorResume(ErrorException.class, e -> {
                                // decode는 숨기지 말고 터뜨림 (내부 버그/DTO 불일치)
                                if (e.getErrorCode() == ErrorCode.KIS_DECODE_ERROR) {
                                    return Mono.error(e);
                                }

                                // http/biz/market_closed는 글로벌 폴백 허용
                                if (e.getErrorCode() == ErrorCode.KIS_HTTP_ERROR
                                        || e.getErrorCode() == ErrorCode.KIS_BIZ_ERROR
                                        || e.getErrorCode() == ErrorCode.KIS_MARKET_CLOSED) {
                                    return globalStockClient.fetchCandlesByMkstackCode(stockCode, freq, from, to);
                                }

                                return Mono.error(e);
                            });

                    return fromGlobal.flatMap(dtoList -> {
                        if (dtoList == null || dtoList.isEmpty()) {
                            return Mono.just(List.<PriceOhlcv>of());
                        }

                        return Mono.fromCallable(() -> {
                                    List<PriceOhlcv> entities = dtoList.stream()
                                            .map(dto -> toPriceOhlcvEntity(stock, freq, dto))
                                            .collect(Collectors.toList());
                                    return priceOhlcvRepository.saveAll(entities);
                                })
                                .subscribeOn(Schedulers.boundedElastic());
                    });
                });
    }

    private PriceOhlcv toPriceOhlcvEntity(Stock stock, Freq reqFreq, PriceOhlcvDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("PriceOhlcvDto is null for stock=" + stock.getStockCode());
        }
        if (dto.getTs() == null) {
            throw new IllegalStateException("PriceOhlcv ts is null for stock=" + stock.getStockCode());
        }

        // dto.freq가 비어있으면 요청 freq로 보정 (저장 안정성)
        Freq resolvedFreq = (dto.getFreq() != null) ? dto.getFreq() : reqFreq;
        if (resolvedFreq == null) {
            throw new IllegalStateException("PriceOhlcv freq is null for stock=" + stock.getStockCode());
        }

        PriceOhlcvId id = new PriceOhlcvId(
                stock.getStockId(),
                dto.getTs(),
                resolvedFreq
        );

        PriceOhlcv entity = new PriceOhlcv();
        entity.setId(id);
        entity.setStock(stock);
        entity.setOpen(dto.getOpen());
        entity.setHigh(dto.getHigh());
        entity.setLow(dto.getLow());
        entity.setClose(dto.getClose());
        entity.setVolume(dto.getVolume());
        return entity;
    }

    private FinancialSummaryDto toFinancialSummaryDto(Financial f) {
        Integer q = null;
        Integer h = null;

        if (f.getPeriodType() == PeriodType.Q) {
            q = (f.getFiscalQuarter() != null) ? f.getFiscalQuarter() : f.getPeriodNo();
        } else if (f.getPeriodType() == PeriodType.H) {
            h = f.getPeriodNo();
        }

        Double debtRatio = null;
        if (f.getLiabilities() != null && f.getEquity() != null && f.getEquity().signum() != 0) {
            debtRatio = f.getLiabilities()
                    .divide(f.getEquity(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        return FinancialSummaryDto.builder()
                .fiscalYear(f.getFiscalYear())
                .fiscalQuarter(q)
                .fiscalHalf(h)
                .periodNo(f.getPeriodNo())
                .periodType(f.getPeriodType().name())
                .reportDate(f.getReportDate())

                .revenue(f.getRevenue())
                .grossProfit(f.getGrossProfit())
                .operatingIncome(f.getOperatingIncome())
                .netIncome(f.getNetIncome())
                .assets(f.getAssets())
                .liabilities(f.getLiabilities())
                .equity(f.getEquity())
                .capitalStock(f.getCapitalStock())
                .retainedEarnings(f.getRetainedEarnings())
                .cashAndEquivalents(f.getCashAndEquivalents())
                .marketCap(f.getMarketCap())

                .operatingMargin(bdToDouble(f.getOperatingMargin()))
                .netMargin(bdToDouble(f.getNetMargin()))
                .roe(bdToDouble(f.getRoe()))
                .per(bdToDouble(f.getPer()))
                .pbr(bdToDouble(f.getPbr()))
                .debtRatio(debtRatio)
                .build();
    }

    private FeatOneRequestDto buildFeatOneRequestDto(
            Stock stock,
            Freq freq,
            List<PriceOhlcv> candles,
            List<Financial> financials,
            Boolean includeExplain
    ) {
        List<OhlcvItemDto> ohlcvDtos =
                (candles == null ? List.<PriceOhlcv>of() : candles).stream()
                        // 복합키의 ts 기준 오름차순 정렬
                        .sorted(Comparator.comparing(o -> o.getId().getTs()))
                        .map(this::toOhlcvItemDto)
                        .toList();

        List<FinancialSummaryDto> financialDtos =
                (financials == null ? List.<Financial>of() : financials).stream()
                        .map(this::toFinancialSummaryDto)
                        .toList();

        boolean explain = Boolean.TRUE.equals(includeExplain);

        return FeatOneRequestDto.builder()
                .stockCode(stock.getStockCode())
                .freq(freq)
                .ohlcv(ohlcvDtos)
                .financials(financialDtos)
                .includeExplain(explain)
                .build();
    }

    private String toKisMarketDivCode(Exchange exchange) {
        return switch (exchange.getCode()) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }

    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private OhlcvItemDto toOhlcvItemDto(PriceOhlcv entity) {
        return OhlcvItemDto.builder()
                .t(entity.getId().getTs())
                .o(entity.getOpen())
                .h(entity.getHigh())
                .l(entity.getLow())
                .c(entity.getClose())
                .v(entity.getVolume())
                .build();
    }

    private FeatOneAnalysisResponseDto buildFallbackResponse(
            Stock stock,
            List<PriceOhlcv> candles,
            List<Financial> financials,
            Boolean includeExplain,
            String analysisApiWarn
    ) {
        List<String> warnings = buildWarnings(
                includeExplain,
                (analysisApiWarn != null && !analysisApiWarn.isBlank()) ? analysisApiWarn : "ANALYSIS_API_FAILED",
                "INDICATOR_CALC_FAILED"
        );

        FeatOneAnalysisMetricsDto metrics = buildMetrics(stock.getStockCode(), candles, financials, warnings);

        FeatOneAnalysisExplainDto explain = null; // includeExplain true여도 fallback에서는 null 유지

        return FeatOneAnalysisResponseDto.builder()
                .metrics(metrics)
                .explain(explain)
                .warnings(warnings)
                .build();
    }

    private FeatOneAnalysisMetricsDto buildMetrics(
            String stockCode,
            List<PriceOhlcv> candles,
            List<Financial> financials,
            List<String> warnings
    ) {
        OhlcvSummaryDto ohlcvSummary = buildOhlcvSummary(candles);
        FinancialSummaryMetricsDto financialSummary = buildFinancialSummary(financials);

        IndicatorBundleDto indicators = IndicatorBundleDto.builder()
                .ema(Collections.emptyMap()) // EMA Map 계약
                .bb20_2(null)
                .stoch14_3_3(null)
                .warnings(warnings != null ? warnings : new ArrayList<>())
                .build();

        return FeatOneAnalysisMetricsDto.builder()
                .stockCode(stockCode)
                .asOf(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .ohlcvSummary(ohlcvSummary)
                .financialSummary(financialSummary)
                .indicators(indicators)
                .indicatorSummary(null)
                .schemaVersion(SCHEMA_VERSION)
                .build();
    }

    // overload
    private FeatOneAnalysisMetricsDto buildMetrics(
            String stockCode,
            List<PriceOhlcv> candles,
            List<Financial> financials
    ) {
        return buildMetrics(stockCode, candles, financials, new ArrayList<>());
    }

    private OhlcvSummaryDto buildOhlcvSummary(List<PriceOhlcv> candles) {
        if (candles == null || candles.isEmpty()) {
            return OhlcvSummaryDto.builder()
                    .count(0)
                    .build();
        }

        PriceOhlcv first = candles.stream()
                .min((a, b) -> a.getId().getTs().compareTo(b.getId().getTs()))
                .orElse(candles.get(0));
        PriceOhlcv last = candles.stream()
                .max((a, b) -> a.getId().getTs().compareTo(b.getId().getTs()))
                .orElse(candles.get(candles.size() - 1));

        return OhlcvSummaryDto.builder()
                .count(candles.size())
                .from(first.getId().getTs())
                .to(last.getId().getTs())
                .lastClose(last.getClose())
                .build();
    }

    private FinancialSummaryMetricsDto buildFinancialSummary(List<Financial> financials) {
        if (financials == null || financials.isEmpty()) {
            return FinancialSummaryMetricsDto.builder()
                    .years(List.of())
                    .revenue(new java.util.HashMap<>())
                    .operatingIncome(new java.util.HashMap<>())
                    .netIncome(new java.util.HashMap<>())
                    .build();
        }

        Map<Integer, BigDecimal> revenue = new java.util.HashMap<>();
        Map<Integer, BigDecimal> operatingIncome = new java.util.HashMap<>();
        Map<Integer, BigDecimal> netIncome = new java.util.HashMap<>();

        for (Financial f : financials) {
            Integer year = f.getFiscalYear();
            if (year == null) continue;

            revenue.put(year, f.getRevenue());
            operatingIncome.put(year, f.getOperatingIncome());
            netIncome.put(year, f.getNetIncome());
        }

        List<Integer> years = revenue.keySet().stream().sorted().toList();

        return FinancialSummaryMetricsDto.builder()
                .years(years)
                .revenue(revenue)
                .operatingIncome(operatingIncome)
                .netIncome(netIncome)
                .build();
    }

    private List<String> buildWarnings(Boolean includeExplain, String... warnings) {
        List<String> result = new ArrayList<>();

        if (warnings != null) {
            result.addAll(Arrays.asList(warnings));
        }

        // explain 관련 warning 추가 가능
        if (includeExplain != null && includeExplain) {
            // 예: result.add("EXPLAIN_SKIPPED");
        }

        return result;
    }

    /**
     * AnalysisApiClient(WebClient) 호출 실패 원인을 warnings에 실어 보내기 위한 변환기.
     * 실제 원인은 이 문자열 1개로 확정 가능해진다.
     */
    private String toAnalysisApiWarn(Throwable ex) {
        if (ex == null) return "ANALYSIS_API_FAILED";

        // WebClientResponseException: HTTP status + body 보유
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException wex) {
            String body = wex.getResponseBodyAsString();
            if (body == null) body = "";
            body = body.replace("\n", " ").trim();
            if (body.length() > 250) body = body.substring(0, 250);

            int code = wex.getStatusCode().value();
            return "ANALYSIS_API_HTTP_" + code + ":" + body;
        }

        // timeout
        if (ex instanceof TimeoutException
                || ex instanceof io.netty.handler.timeout.ReadTimeoutException
                || ex instanceof io.netty.handler.timeout.WriteTimeoutException) {
            return "ANALYSIS_API_TIMEOUT";
        }

        // connect refused / DNS
        if (ex instanceof ConnectException) {
            return "ANALYSIS_API_CONNECT_FAILED:" + safeMsg(ex);
        }
        if (ex instanceof UnknownHostException) {
            return "ANALYSIS_API_DNS_FAILED:" + safeMsg(ex);
        }

        // json decode / mapping
        if (ex instanceof DecodingException) {
            return "ANALYSIS_API_DECODE_FAILED:" + safeMsg(ex);
        }

        return "ANALYSIS_API_EXCEPTION:" + ex.getClass().getSimpleName() + ":" + safeMsg(ex);
    }

    private String safeMsg(Throwable ex) {
        String m = ex.getMessage();
        if (m == null) return "";
        m = m.replace("\n", " ").trim();
        return m.length() > 120 ? m.substring(0, 120) : m;
    }

    private OffsetDateTime parseRequestDateTime(String raw, boolean endOfDayForDateOnly) {
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ignored) {
            // fall through: support date-only inputs from legacy clients
        }

        try {
            LocalDate date = LocalDate.parse(raw);
            if (endOfDayForDateOnly) {
                return date.atTime(23, 59, 59).atOffset(ZoneOffset.UTC);
            }
            return date.atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException(
                    "from/to must be ISO-8601 datetime (e.g. 2025-01-10T00:00:00Z) or date (e.g. 2025-01-10)."
            );
        }
    }
}
