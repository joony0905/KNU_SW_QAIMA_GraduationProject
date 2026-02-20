package com.qaima.service;

import com.qaima.domain.*;
import com.qaima.dto.*;
import com.qaima.external.AnalysisApiClient;
import com.qaima.external.GlobalStockClient;
import com.qaima.external.KrStockClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.IndicatorValueRepository;
import com.qaima.repository.PriceOhlcvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatOneService {

    private final StockService stockService;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final IndicatorValueRepository indicatorValueRepository;
    private final FinancialRepository financialRepository;
    private final MarketSnapshotService marketSnapshotService;


    private final KrStockClient krStockClient;
    private final GlobalStockClient globalStockClient;
    private final AnalysisApiClient analysisApiClient;

    /**
     * 기능 1 전체 플로우
     * 1) 종목 조회 (없으면 외부 메타로 생성)
     * 2) 캔들 로딩 (DB → 없으면 외부 → DB저장)
     * 3) 지표, 재무 로딩 (현재는 DB)
     * 4) FastAPI 분석 요청
     * 5) 응답 DTO 조립
     */
    public Mono<FeatOneResponseDataDto> getFeatOneData(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        // 1) 종목 조회 + 없으면 외부에서 자동 생성 (한 번만)
        Mono<Stock> stockMono = stockService.getStockByCode(stockCode).cache();

        // 2) 캔들 (DB → 없으면 KIS → 실패 시 Global)
        Mono<List<PriceOhlcv>> candlesMono = stockMono.flatMap(stock ->
                loadCandlesWithFallback(stock, freq, from, to)
        );

        // 3) 지표 (초기엔 DB에 있는 것만)
        Mono<List<IndicatorValue>> indicatorsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                indicatorValueRepository.findByStockAndFreqAndTsBetweenOrderByTs(
                                        stock, freq, from, to
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        int financialLimit = 5; // 테스트용

        Mono<List<Financial>> financialsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                financialRepository.findByStockAndPeriodTypeOrderByFiscalYearDescVersionDesc(
                                        stock,
                                        PeriodType.A,
                                        PageRequest.of(0, financialLimit)
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        // 4) 시장 스냅샷 (KIS inquire-price)  ※ 국내만. 해외는 null 처리
        Mono<MarketSnapshotDto> marketSnapshotMono = stockMono
                .flatMap(stock -> {
                    LocalDate baseDate = LocalDate.now();

                    if (stock.getExchange() == null) return Mono.empty();
                    String div = toKisMarketDivCode(stock.getExchange());
                    if ("B".equals(div)) return Mono.empty();

                    return krStockClient.fetchKisStatRaw(stock.getStockCode(), div)
                            .flatMap(output -> {
                                MarketSnapshotDto dto = toMarketSnapshotDto(output, baseDate);

                                Mono<Void> saveMono = marketSnapshotService
                                        .upsertFromKis(stock, output, dto.getAsOfDate())
                                        .doOnError(e -> log.warn("MarketSnapshot upsert failed. stockCode={}", stock.getStockCode(), e))
                                        .onErrorResume(e -> Mono.empty())
                                        .then();

                                return saveMono.thenReturn(dto);
                            })
                            .doOnError(ex -> log.warn("KIS market snapshot failed. stockCode={}, div={}",
                                    stock.getStockCode(), div, ex))
                            .onErrorResume(ex -> marketSnapshotService.getLatestDto(stock, baseDate));
                })
                .cache();



        // 5) DB + 외부 데이터 → FastAPI 요청 → 응답 DTO 조립
        Mono<Optional<MarketSnapshotDto>> marketSnapshotMonoOpt = marketSnapshotMono
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        return Mono.zip(
                        stockMono,
                        candlesMono,
                        indicatorsMono,
                        financialsMono,
                        marketSnapshotMonoOpt
                )
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    List<PriceOhlcv> candles = tuple.getT2();
                    List<IndicatorValue> indicators = tuple.getT3();
                    List<Financial> financials = tuple.getT4();
                    MarketSnapshotDto marketSnapshot = tuple.getT5().orElse(null);

                    FeatOneRequestDto requestDto = buildFeatOneRequestDto(
                            stock, candles, indicators, financials
                    );

                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(textDto -> buildFeatOneResponseDto(
                                    stock,
                                    candles,
                                    indicators,
                                    financials,
                                    marketSnapshot,
                                    textDto
                            ));
                });
    }

    /**
     * 1) DB에서 캔들 조회
     * 2) 비어 있으면 KIS 캔들 호출
     * 3) KIS 실패 시 Global(Marketstack) 폴백
     * 4) 외부에서 가져온 건 DB에 저장 후 PriceOhlcv 리스트 반환
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
                        priceOhlcvRepository.findByStockCodeAndFreqAndTsBetween(
                                stockCode, freq, from, to
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> {
                    if (!existing.isEmpty()) {
                        return Mono.just(existing);
                    }

                    Mono<List<PriceOhlcvDto>> fromKis =
                            krStockClient.fetchCandles(stockCode, marketDivCode, freq, from, to);

                    Mono<List<PriceOhlcvDto>> fromGlobal =
                            fromKis.onErrorResume(ex ->
                                    globalStockClient.fetchCandles(stockCode, freq, from, to)
                            );

                    return fromGlobal.flatMap(dtoList -> {
                        if (dtoList == null || dtoList.isEmpty()) {
                            return Mono.just(List.<PriceOhlcv>of());
                        }

                        return Mono.fromCallable(() -> {
                                    List<PriceOhlcv> entities = dtoList.stream()
                                            .map(dto -> toPriceOhlcvEntity(stock, dto))
                                            .collect(Collectors.toList());

                                    return priceOhlcvRepository.saveAll(entities);
                                })
                                .subscribeOn(Schedulers.boundedElastic());
                    });
                });
    }

    // ===== 매핑 로직 =====

    private PriceOhlcv toPriceOhlcvEntity(Stock stock, PriceOhlcvDto dto) {
        PriceOhlcvId id = new PriceOhlcvId(
                stock.getStockId(),
                dto.getTs(),
                dto.getFreq()
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

        Double operatingMargin = ratioPct(f.getOperatingIncome(), f.getRevenue());
        Double netMargin = ratioPct(f.getNetIncome(), f.getRevenue());
        Double roe = ratioPct(f.getNetIncome(), f.getEquity());
        Double debtRatio = ratioPct(f.getLiabilities(), f.getEquity());

        // PER, PBR, MarketCap은 시장 값이므로 FinancialSummary에서는 null
        Double per = null;
        Double pbr = null;

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
                .marketCap(null)

                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .roe(roe)
                .per(per)
                .pbr(pbr)
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
        List<PriceOhlcvDto> candleDtos = candles.stream()
                .map(this::toPriceOhlcvDto)
                .toList();

        List<IndicatorValueDto> indicatorDtos = indicators.stream()
                .map(this::toIndicatorValueDto)
                .toList();

        List<FinancialSummaryDto> financialDtos = financials.stream()
                .map(this::toFinancialSummaryDto)
                .toList();

        StockDto stockDto = toStockDto(stock);

        return FeatOneRequestDto.builder()
                .stock(stockDto)
                .candles(candleDtos)
                .indicators(indicatorDtos)
                .financials(financialDtos)
                .build();
    }

    // 응답 DTO에만 marketSnapshot 포함
    private FeatOneResponseDataDto buildFeatOneResponseDto(
            Stock stock,
            List<PriceOhlcv> candles,
            List<IndicatorValue> indicators,
            List<Financial> financials,
            MarketSnapshotDto marketSnapshot,
            FeatOneResponseTextDto textDto
    ) {
        List<PriceOhlcvDto> candleDtos = candles.stream()
                .map(this::toPriceOhlcvDto)
                .toList();

        List<IndicatorValueDto> indicatorDtos = indicators.stream()
                .map(this::toIndicatorValueDto)
                .toList();

        List<FinancialSummaryDto> financialDtos = financials.stream()
                .map(this::toFinancialSummaryDto)
                .toList();

        StockDto stockDto = toStockDto(stock);

        return FeatOneResponseDataDto.builder()
                .stock(stockDto)
                .candles(candleDtos)
                .indicators(indicatorDtos)
                .financials(financialDtos)
                .marketSnapshot(marketSnapshot)
                .analysis(textDto)
                .build();
    }

    // 거래소 → KIS marketDivCode 매핑
    private String toKisMarketDivCode(Exchange exchange) {
        return switch (exchange.getCode()) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }

    // 파생지표 계산 유틸
    private static Double ratioPct(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null) return null;
        if (denominator.signum() == 0) return null;
        return numerator
                .divide(denominator, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private MarketSnapshotDto toMarketSnapshotDto(KisStatResponseDto.Output o, LocalDate asOfDate) {
        if (o == null) return null;

        return MarketSnapshotDto.builder()
                .asOfDate(asOfDate)
                .marketCap(parseNullableBigDecimal(o.getHts_avls()))
                .per(parseNullableDouble(o.getPer()))
                .pbr(parseNullableDouble(o.getPbr()))
                .sharesOutstanding(parseNullableBigDecimal(o.getLstn_stcn()))
                .source("KIS")
                .build();
    }

    private static BigDecimal parseNullableBigDecimal(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        t = t.replace(",", "");
        try {
            return new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double parseNullableDouble(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        t = t.replace(",", "");
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
