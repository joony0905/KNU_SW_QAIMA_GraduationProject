package com.qaima.service;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.*;
import com.qaima.dto.*;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.GlobalStockClient;
import com.qaima.external.KrStockClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.IndicatorValueRepository;
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
    private final IndicatorValueRepository indicatorValueRepository;
    private final FinancialRepository financialRepository;

    // Feature1은 (너가 현재 유지 중인 구조대로) 직접 KIS/Marketstack을 사용
    private final KrStockClient krStockClient;
    private final GlobalStockClient globalStockClient;
    private final AnalysisApiClient analysisApiClient;

    public Mono<FeatOneResult> getFeatOneData(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        Mono<Stock> stockMono = stockService.getOrCreateStockByCode(stockCode).cache();

        Mono<List<PriceOhlcv>> candlesMono = stockMono.flatMap(stock ->
                loadCandlesWithFallback(stock, freq, from, to)
        );

        Mono<List<IndicatorValue>> indicatorsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                indicatorValueRepository.findByStockAndFreqAndTsBetweenOrderByTs(
                                        stock, freq, from, to
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
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

        return Mono.zip(stockMono, candlesMono, indicatorsMono, financialsMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    List<PriceOhlcv> candles = tuple.getT2();
                    List<IndicatorValue> indicators = tuple.getT3();
                    List<Financial> financials = tuple.getT4();

                    FeatOneRequestDto requestDto =
                            buildFeatOneRequestDto(stock, candles, indicators, financials);

                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(textDto -> {
                                FeatOneResponseDataDto data =
                                        buildFeatOneResponseDto(stock, candles, indicators, financials, textDto);

                                boolean chartUnavailable = (candles == null || candles.isEmpty());
                                return new FeatOneResult(data, chartUnavailable);
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
            OffsetDateTime to
    ) {
        String stockCode = stock.getStockCode();
        String marketDivCode = toKisMarketDivCode(stock.getExchange());

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

    private PriceOhlcvDto toPriceOhlcvDto(PriceOhlcv entity) {
        return PriceOhlcvDto.builder()
                .ts(entity.getId().getTs())
                .freq(entity.getId().getFreq())
                .open(entity.getOpen())
                .high(entity.getHigh())
                .low(entity.getLow())
                .close(entity.getClose())
                .volume(entity.getVolume())
                .build();
    }

    private IndicatorValueDto toIndicatorValueDto(IndicatorValue iv) {
        return IndicatorValueDto.builder()
                .ts(iv.getTs())
                .freq(iv.getFreq())
                .key(iv.getKey())
                .valueNum(iv.getValueNum())
                .valueJson(iv.getValueJson())
                .build();
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

    private StockDto toStockDto(Stock stock) {
        if (stock == null) return null;

        return StockDto.builder()
                .stockId(stock.getStockId())
                .stockCode(stock.getStockCode())
                .isin(stock.getIsin())
                .companyName(stock.getCompanyName())
                .exchangeId(stock.getExchange() != null ? stock.getExchange().getExchangeId() : null)
                .exchangeCode(stock.getExchange() != null ? stock.getExchange().getCode() : null)
                .assetType(stock.getAssetType())
                .currency(stock.getCurrency())
                .industryId(stock.getIndustry() != null ? stock.getIndustry().getIndustryId() : null)
                .listedAt(stock.getListedAt())
                .delistedAt(stock.getDelistedAt())
                .build();
    }

    private FeatOneRequestDto buildFeatOneRequestDto(
            Stock stock,
            List<PriceOhlcv> candles,
            List<IndicatorValue> indicators,
            List<Financial> financials
    ) {
        List<PriceOhlcvDto> candleDtos = candles.stream().map(this::toPriceOhlcvDto).toList();
        List<IndicatorValueDto> indicatorDtos = indicators.stream().map(this::toIndicatorValueDto).toList();
        List<FinancialSummaryDto> financialDtos = financials.stream().map(this::toFinancialSummaryDto).toList();

        return FeatOneRequestDto.builder()
                .stock(toStockDto(stock))
                .candles(candleDtos)
                .indicators(indicatorDtos)
                .financials(financialDtos)
                .build();
    }

    private FeatOneResponseDataDto buildFeatOneResponseDto(
            Stock stock,
            List<PriceOhlcv> candles,
            List<IndicatorValue> indicators,
            List<Financial> financials,
            FeatOneResponseTextDto textDto
    ) {
        List<PriceOhlcvDto> candleDtos = candles.stream().map(this::toPriceOhlcvDto).toList();
        List<IndicatorValueDto> indicatorDtos = indicators.stream().map(this::toIndicatorValueDto).toList();
        List<FinancialSummaryDto> financialDtos = financials.stream().map(this::toFinancialSummaryDto).toList();

        return FeatOneResponseDataDto.builder()
                .stock(toStockDto(stock))
                .candles(candleDtos)
                .indicators(indicatorDtos)
                .financials(financialDtos)
                .analysis(textDto)
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
}
