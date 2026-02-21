package com.qaima.service;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.*;
import com.qaima.dto.FeatOneAnalysisExplainDto;
import com.qaima.dto.FeatOneAnalysisMetaDto;
import com.qaima.dto.FeatOneAnalysisMetricsDto;
import com.qaima.dto.FeatOneAnalysisResponseDto;
import com.qaima.dto.FeatOneRequestDto;
import com.qaima.dto.FinancialSummaryDto;
import com.qaima.dto.FinancialSummaryMetricsDto;
import com.qaima.dto.IndicatorSlotsDto;
import com.qaima.dto.OhlcvItemDto;
import com.qaima.dto.OhlcvSummaryDto;
import com.qaima.dto.PriceOhlcvDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.GlobalStockClient;
import com.qaima.external.KrStockClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.PriceOhlcvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatOneService {

    private final StockService stockService;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final FinancialRepository financialRepository;

    // Feature1은 (너가 현재 유지 중인 구조대로) 직접 KIS/Marketstack을 사용
    private final KrStockClient krStockClient;
    private final GlobalStockClient globalStockClient;
    private final AnalysisApiClient analysisApiClient;

    public Mono<FeatOneResult> getFeatOneData(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to,
            String marketDivCode,
            Boolean includeExplain
    ) {
        Mono<Stock> stockMono = stockService.getOrCreateStockByCode(stockCode).cache();

        Mono<List<PriceOhlcv>> candlesMono = stockMono.flatMap(stock ->
                loadCandlesWithFallback(stock, freq, from, to, marketDivCode)
        );

        int financialLimit = 5;

        Mono<List<Financial>> financialsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                financialRepository.findByStockOrderByReportDateDescVersionDesc(
                                        stock,
                                        PageRequest.of(0, financialLimit)
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        return Mono.zip(stockMono, candlesMono, financialsMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    List<PriceOhlcv> candles = tuple.getT2();
                    List<Financial> financials = tuple.getT3();

                    FeatOneRequestDto requestDto =
                            buildFeatOneRequestDto(stock, freq, candles, financials, includeExplain);

                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(response -> {
                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return new FeatOneResult(response, chartUnavailable);
                            })
                            .onErrorResume(ex -> {
                                FeatOneAnalysisResponseDto fallback =
                                        buildFallbackResponse(stock, candles, financials, includeExplain);
                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return Mono.just(new FeatOneResult(fallback, chartUnavailable));
                            });
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

        return Mono.fromCallable(() ->
                        priceOhlcvRepository.findByStockCodeAndFreqAndTsBetween(stockCode, freq, from, to)
                )
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
                                    return globalStockClient.fetchCandles(stockCode, freq, from, to);
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
        List<OhlcvItemDto> ohlcvDtos = candles.stream()
                .map(this::toOhlcvItemDto)
                .toList();
        List<FinancialSummaryDto> financialDtos = financials.stream().map(this::toFinancialSummaryDto).toList();

        return FeatOneRequestDto.builder()
                .stockCode(stock.getStockCode())
                .freq(freq)
                .ohlcv(ohlcvDtos)
                .financials(financialDtos)
                .includeExplain(includeExplain != null && includeExplain)
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
            Boolean includeExplain
    ) {
        FeatOneAnalysisMetricsDto metrics = buildMetrics(stock.getStockCode(), candles, financials);

        FeatOneAnalysisMetaDto meta = FeatOneAnalysisMetaDto.builder()
                .warnings(buildWarnings(includeExplain, "ANALYSIS_API_FAILED"))
                .build();

        FeatOneAnalysisExplainDto explain = null;
        if (includeExplain != null && includeExplain) {
            explain = null;
        }

        return FeatOneAnalysisResponseDto.builder()
                .metrics(metrics)
                .explain(explain)
                .meta(meta)
                .build();
    }

    private FeatOneAnalysisMetricsDto buildMetrics(
            String stockCode,
            List<PriceOhlcv> candles,
            List<Financial> financials
    ) {
        OhlcvSummaryDto ohlcvSummary = buildOhlcvSummary(candles);
        FinancialSummaryMetricsDto financialSummary = buildFinancialSummary(financials);

        return FeatOneAnalysisMetricsDto.builder()
                .stockCode(stockCode)
                .asOf(OffsetDateTime.now())
                .ohlcvSummary(ohlcvSummary)
                .financialSummary(financialSummary)
                .indicators(IndicatorSlotsDto.builder().build())
                .schemaVersion("0.1")
                .build();
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

        java.util.Map<Integer, java.math.BigDecimal> revenue = new java.util.HashMap<>();
        java.util.Map<Integer, java.math.BigDecimal> operatingIncome = new java.util.HashMap<>();
        java.util.Map<Integer, java.math.BigDecimal> netIncome = new java.util.HashMap<>();

        for (Financial f : financials) {
            Integer year = f.getFiscalYear();
            if (year == null) {
                continue;
            }
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

    private java.util.List<String> buildWarnings(Boolean includeExplain, String baseWarning) {
        java.util.List<String> warnings = new java.util.ArrayList<>();
        if (baseWarning != null) {
            warnings.add(baseWarning);
        }
        if (includeExplain == null || !includeExplain) {
            warnings.add("LLM_EXPLAIN_SKIPPED");
        } else {
            warnings.add("LLM_EXPLAIN_FAILED");
        }
        return warnings;
    }
}
