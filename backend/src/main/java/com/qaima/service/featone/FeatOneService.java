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
import com.qaima.dto.featone.FeatOneFinancialPointDto;
import com.qaima.dto.featone.FeatOneFinancialSeriesDto;
import com.qaima.dto.featone.FeatOneGrowthDto;
import com.qaima.dto.featone.FeatOneMarketContextDto;
import com.qaima.dto.featone.FeatOneMarketSnapshotDto;
import com.qaima.dto.featone.FeatOnePerShareDto;
import com.qaima.dto.featone.FeatOneProfitabilityDto;
import com.qaima.dto.featone.FeatOneRequestDto;
import com.qaima.dto.featone.FeatOneStabilityDto;
import com.qaima.dto.featone.FeatOneValuationDto;
import com.qaima.dto.ohlcv.OhlcvItemDto;
import com.qaima.dto.ohlcv.OhlcvSummaryDto;
import com.qaima.dto.ohlcv.PriceOhlcvDto;
import com.qaima.dto.stock.MarketSnapshotDto;
import com.qaima.dto.indicator.IndicatorBundleDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.GlobalStockClient;
import com.qaima.external.KrStockClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.service.stock.MarketSnapshotService;
import com.qaima.service.stock.StockService;
import com.qaima.service.tradingcalendar.TradingCalendarService;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatOneService {

    private static final Logger log = LoggerFactory.getLogger(FeatOneService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String KRX_MARKET = "KRX";

    private static final int DEFAULT_FINANCIAL_LIMIT = 12;
    private static final String SCHEMA_VERSION = "0.1"; //network에서 0.1로 떨어지면 백엔드 오류

    private final StockService stockService;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final FinancialRepository financialRepository;
    private final MarketSnapshotService marketSnapshotService;
    private final TradingCalendarService tradingCalendarService;

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
            Boolean includeExplain,
            String llmVendor
    ) {
        if (stockCode == null || stockCode.isBlank()
                || freq == null
                || from == null
                || to == null
                || marketDivCode == null
                || includeExplain == null) {

            log.warn("[FeatOneService param validation fail] stockCode={}, freq={}, from={}, to={}, marketDivCode={}, includeExplain={}, llmVendor={}",
                    stockCode, freq, from, to, marketDivCode, includeExplain, llmVendor);
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

        Mono<Optional<MarketSnapshotDto>> marketSnapshotMono = stockMono
                .flatMap(stock -> prepareMarketSnapshot(stock, marketDivCode))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(stockMono, candlesMono, financialsMono, marketSnapshotMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    List<PriceOhlcv> candles = tuple.getT2();
                    List<Financial> financials = tuple.getT3();
                    MarketSnapshotDto marketSnapshot = tuple.getT4().orElse(null);

                    FeatOneRequestDto requestDto =
                            buildFeatOneRequestDto(stock, freq, from, to, candles, financials, marketSnapshot, includeExplain, llmVendor);

                    log.info("[FeatOneService][analysis-request] stockCode={}, freq={}, ohlcvSize={}, financialsSize={}, includeExplain={}, llmVendor={}",
                            requestDto.getStockCode(),
                            requestDto.getFreq(),
                            requestDto.getOhlcv() != null ? requestDto.getOhlcv().size() : 0,
                            requestDto.getFinancials() != null ? requestDto.getFinancials().size() : 0,
                            requestDto.getIncludeExplain(),
                            requestDto.getLlmVendor());

                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(response -> {
                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return new FeatOneResult(response, chartUnavailable);
                            })
                            .onErrorResume(ex -> {
                                // indicator가 사라지는 현상은 여기로 떨어져 fallback이 내려가면서 발생한다.
                                String apiWarn = toAnalysisApiWarn(ex);

                                log.error("[Feature1] Analysis API failed. stockCode={}, freq={}, from={}, to={}, includeExplain={}, llmVendor={}, warn={}",
                                        stockCode, freq, from, to, includeExplain, llmVendor, apiWarn, ex);

                                FeatOneAnalysisResponseDto fallback =
                                        buildFallbackResponse(stock, candles, financials, marketSnapshot, includeExplain, apiWarn);

                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return Mono.just(new FeatOneResult(fallback, chartUnavailable));
                            });
                });
    }

    private Mono<MarketSnapshotDto> prepareMarketSnapshot(Stock stock, String marketDivCodeOverride) {
        if (stock == null || stock.getExchange() == null) {
            return Mono.empty();
        }

        LocalDate baseDate = LocalDate.now(KST);
        return marketSnapshotService.getLatestDto(stock, baseDate)
                .onErrorResume(ex -> {
                    log.warn("[FeatOneService] market snapshot read failed. stockCode={}",
                            stock.getStockCode(), ex);
                    return Mono.empty();
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
                    if (!shouldFetchCandlesFromExternal(existing)) {
                        log.info("[FeatOneService][candles] DB hit covers latest request. stockCode={}, freq={}, size={}, latest={}",
                                stockCode,
                                freq,
                                existing.size(),
                                latestLocalDate(existing));
                        return Mono.just(existing);
                    }

                    log.info("[FeatOneService][candles] latest candle miss -> external fallback. stockCode={}, freq={}, existingSize={}, requestedToDate={}, latestDbDate={}",
                            stockCode,
                            freq,
                            existing.size(),
                            latestTradingDay(),
                            latestLocalDate(existing));

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
                            return Mono.just(existing);
                        }

                        return Mono.fromCallable(() -> {
                                    List<PriceOhlcv> entities = dtoList.stream()
                                            .map(dto -> toPriceOhlcvEntity(stock, freq, dto))
                                            .collect(Collectors.toList());

                                    List<PriceOhlcv> merged = mergeCandles(existing, entities);
                                    List<PriceOhlcv> missingOnly = filterMissingCandles(existing, entities);

                                    if (missingOnly.isEmpty()) {
                                        log.info("[FeatOneService][candles] external returned only existing rows. stockCode={}, freq={}, fetchedSize={}",
                                                stockCode, freq, entities.size());
                                        return merged;
                                    }

                                    List<PriceOhlcv> saved = priceOhlcvRepository.saveAll(missingOnly);
                                    log.info("[FeatOneService][candles] persisted missing rows only. stockCode={}, freq={}, existingSize={}, fetchedSize={}, insertedSize={}",
                                            stockCode, freq, existing.size(), entities.size(), saved.size());
                                    return mergeCandles(existing, saved);
                                })
                                .subscribeOn(Schedulers.boundedElastic());
                    });
                });
    }

    private boolean shouldFetchCandlesFromExternal(List<PriceOhlcv> existing) {
        if (existing == null || existing.isEmpty()) {
            return true;
        }
 
        LocalDate latestRequestedDate = latestTradingDay();
        LocalDate latestDbDate = latestLocalDate(existing);
        return latestDbDate == null || latestDbDate.isBefore(latestRequestedDate);
    }

    private LocalDate latestTradingDay() {
        return tradingCalendarService.latestTradingDay(ZonedDateTime.now(KST), KRX_MARKET);
    }

    private LocalDate latestLocalDate(List<PriceOhlcv> candles) {
        if (candles == null || candles.isEmpty()) {
            return null;
        }

        return candles.stream()
                .map(candle -> candle != null && candle.getId() != null ? candle.getId().getTs() : null)
                .filter(java.util.Objects::nonNull)
                .map(OffsetDateTime::toLocalDate)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private List<PriceOhlcv> filterMissingCandles(List<PriceOhlcv> existing, List<PriceOhlcv> fetched) {
        Set<PriceOhlcvId> existingIds = (existing == null ? List.<PriceOhlcv>of() : existing).stream()
                .map(PriceOhlcv::getId)
                .collect(Collectors.toCollection(HashSet::new));

        return (fetched == null ? List.<PriceOhlcv>of() : fetched).stream()
                .filter(entity -> entity.getId() != null && !existingIds.contains(entity.getId()))
                .toList();
    }

    private List<PriceOhlcv> mergeCandles(List<PriceOhlcv> existing, List<PriceOhlcv> fetched) {
        java.util.LinkedHashMap<PriceOhlcvId, PriceOhlcv> merged = new java.util.LinkedHashMap<>();

        if (existing != null) {
            existing.stream()
                    .filter(entity -> entity != null && entity.getId() != null)
                    .forEach(entity -> merged.put(entity.getId(), entity));
        }

        if (fetched != null) {
            fetched.stream()
                    .filter(entity -> entity != null && entity.getId() != null)
                    .forEach(entity -> merged.put(entity.getId(), entity));
        }

        return merged.values().stream()
                .sorted(Comparator.comparing(entity -> entity.getId().getTs()))
                .toList();
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

    private FeatOneFinancialPointDto toFeature1FinancialPointDto(Financial f) {
        Integer q = null;
        Integer h = null;

        if (f.getPeriodType() == PeriodType.Q) {
            q = (f.getFiscalQuarter() != null) ? f.getFiscalQuarter() : f.getPeriodNo();
        } else if (f.getPeriodType() == PeriodType.H) {
            h = f.getPeriodNo();
        }

        return FeatOneFinancialPointDto.builder()
                .fiscalYear(f.getFiscalYear())
                .fiscalQuarter(q)
                .fiscalHalf(h)
                .periodNo(f.getPeriodNo())
                .periodType(f.getPeriodType().name())
                .reportDate(f.getReportDate())
                .revenue(f.getRevenue())
                .operatingIncome(f.getOperatingIncome())
                .netIncome(f.getNetIncome())
                .assets(f.getAssets())
                .liabilities(f.getLiabilities())
                .equity(f.getEquity())
                .currentAssets(f.getCurrentAssets())
                .currentLiabilities(f.getCurrentLiabilities())
                .inventories(f.getInventories())
                .interestExpense(f.getInterestExpense())
                .operatingCashFlow(f.getOperatingCashFlow())
                .capex(sumNullable(f.getCapexPpe(), f.getCapexIntangible()))
                .build();
    }

    private FeatOneRequestDto buildFeatOneRequestDto(
            Stock stock,
            Freq freq,
            String from,
            String to,
            List<PriceOhlcv> candles,
            List<Financial> financials,
            MarketSnapshotDto marketSnapshot,
            Boolean includeExplain,
            String llmVendor
    ) {
        List<OhlcvItemDto> ohlcvDtos =
                (candles == null ? List.<PriceOhlcv>of() : candles).stream()
                        // 복합키의 ts 기준 오름차순 정렬
                        .sorted(Comparator.comparing(o -> o.getId().getTs()))
                        .map(this::toOhlcvItemDto)
                        .toList();

        List<FeatOneFinancialPointDto> financialDtos =
                (financials == null ? List.<Financial>of() : financials).stream()
                        .map(this::toFeature1FinancialPointDto)
                        .toList();

        boolean explain = Boolean.TRUE.equals(includeExplain);

        return FeatOneRequestDto.builder()
                .stockCode(stock.getStockCode())
                .freq(freq)
                .from(from)
                .to(to)
                .ohlcv(ohlcvDtos)
                .financials(financialDtos)
                .marketContext(FeatOneMarketContextDto.builder()
                        .asOf(marketSnapshot != null ? marketSnapshot.getAsOfDate() : null)
                        .currency(stock.getCurrency())
                        .sharesOutstanding(marketSnapshot != null ? marketSnapshot.getSharesOutstanding() : null)
                        .build())
                .marketSnapshot(toFeatOneMarketSnapshot(marketSnapshot, stock))
                .includeExplain(explain)
                .llmVendor(llmVendor)
                .build();
    }

    private String toKisMarketDivCode(Exchange exchange) {
        return switch (exchange.getCode()) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "J";
            case "KONEX" -> "J";
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
            MarketSnapshotDto marketSnapshot,
            Boolean includeExplain,
            String analysisApiWarn
    ) {
        List<String> warnings = buildWarnings(
                includeExplain,
                (analysisApiWarn != null && !analysisApiWarn.isBlank()) ? analysisApiWarn : "ANALYSIS_API_FAILED",
                "INDICATOR_CALC_FAILED"
        );

        FeatOneAnalysisMetricsDto metrics = buildMetrics(stock, candles, financials, marketSnapshot, warnings);

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
        return buildMetrics(null, candles, financials, null, warnings);
    }

    private FeatOneAnalysisMetricsDto buildMetrics(
            Stock stock,
            List<PriceOhlcv> candles,
            List<Financial> financials,
            MarketSnapshotDto marketSnapshot,
            List<String> warnings
    ) {
        OhlcvSummaryDto ohlcvSummary = buildOhlcvSummary(candles);
        FeatOneFinancialSeriesDto financialSeries = buildFinancialSeries(financials);

        IndicatorBundleDto indicators = IndicatorBundleDto.builder()
                .ema(Collections.emptyMap()) // EMA Map 계약
                .bb20_2(null)
                .stoch14_3_3(null)
                .warnings(warnings != null ? warnings : new ArrayList<>())
                .build();

        return FeatOneAnalysisMetricsDto.builder()
                .stockCode(stock != null ? stock.getStockCode() : null)
                .asOf(OffsetDateTime.now(ZoneOffset.UTC).toString())
                .ohlcvSummary(ohlcvSummary)
                .financialSeries(financialSeries)
                .marketSnapshot(toFeatOneMarketSnapshot(marketSnapshot, stock))
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
        return buildMetrics(null, candles, financials, null, new ArrayList<>());
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

    private FeatOneFinancialSeriesDto buildFinancialSeries(List<Financial> financials) {
        if (financials == null || financials.isEmpty()) {
            return FeatOneFinancialSeriesDto.builder()
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

        return FeatOneFinancialSeriesDto.builder()
                .years(years)
                .revenue(revenue)
                .operatingIncome(operatingIncome)
                .netIncome(netIncome)
                .build();
    }

    private FeatOneMarketSnapshotDto toFeatOneMarketSnapshot(MarketSnapshotDto snapshot, Stock stock) {
        if (snapshot == null && stock == null) {
            return null;
        }
        return FeatOneMarketSnapshotDto.builder()
                .asOf(snapshot != null && snapshot.getAsOfDate() != null ? snapshot.getAsOfDate().toString() : null)
                .currency(stock != null ? stock.getCurrency() : null)
                .valuation(FeatOneValuationDto.builder()
                        .per(snapshot != null ? snapshot.getPer() : null)
                        .pbr(snapshot != null ? snapshot.getPbr() : null)
                        .psr(snapshot != null ? snapshot.getPsr() : null)
                        .marketCap(snapshot != null ? snapshot.getMarketCap() : null)
                        .build())
                .profitability(FeatOneProfitabilityDto.builder()
                        .roe(snapshot != null ? snapshot.getRoe() : null)
                        .roa(snapshot != null ? snapshot.getRoa() : null)
                        .operatingMargin(snapshot != null ? snapshot.getOperatingMargin() : null)
                        .netMargin(snapshot != null ? snapshot.getNetMargin() : null)
                        .build())
                .stability(FeatOneStabilityDto.builder()
                        .debtRatio(snapshot != null ? snapshot.getDebtRatio() : null)
                        .currentRatio(snapshot != null ? snapshot.getCurrentRatio() : null)
                        .quickRatio(snapshot != null ? snapshot.getQuickRatio() : null)
                        .interestCoverageRatio(snapshot != null ? snapshot.getInterestCoverageRatio() : null)
                        .build())
                .growth(FeatOneGrowthDto.builder()
                        .revenueGrowth(snapshot != null ? snapshot.getRevenueGrowth() : null)
                        .epsGrowth(snapshot != null ? snapshot.getEpsGrowth() : null)
                        .freeCashFlow(snapshot != null ? snapshot.getFreeCashFlow() : null)
                        .build())
                .perShare(FeatOnePerShareDto.builder()
                        .eps(snapshot != null && snapshot.getEpsTtm() != null ? snapshot.getEpsTtm().doubleValue() : null)
                        .bps(snapshot != null && snapshot.getBps() != null ? snapshot.getBps().doubleValue() : null)
                        .build())
                .build();
    }

    private BigDecimal sumNullable(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return null;
        }
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
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
                return date.atTime(23, 59, 59).atZone(KST).toOffsetDateTime();
            }
            return date.atStartOfDay(KST).toOffsetDateTime();
        } catch (DateTimeParseException ignored) {
            throw new IllegalArgumentException(
                    "from/to must be ISO-8601 datetime (e.g. 2025-01-10T00:00:00Z) or date (e.g. 2025-01-10)."
            );
        }
    }
}
