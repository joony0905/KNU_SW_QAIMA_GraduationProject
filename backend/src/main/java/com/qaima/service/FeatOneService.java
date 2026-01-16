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

    private final StockService stockService;                 // Stock 생성 관련
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final IndicatorValueRepository indicatorValueRepository;
    private final FinancialRepository financialRepository;

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
    public Mono<FeatOneResult> getFeatOneData(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        // 1. 종목 조회 + 없으면 외부에서 자동 생성 (한 번만)
        Mono<Stock> stockMono = stockService.getOrCreateStockByCode(stockCode).cache();

        // 2. 캔들 (DB → 없으면 KIS → 실패 시 Global)
        Mono<List<PriceOhlcv>> candlesMono = stockMono.flatMap(stock ->
                loadCandlesWithFallback(stock, freq, from, to)
        );

        // 3. 지표 (초기엔 DB에 있는 것만 나중에 FastAPI 계산과 혼합 가능)
        Mono<List<IndicatorValue>> indicatorsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                indicatorValueRepository.findByStockAndFreqAndTsBetweenOrderByTs(
                                        stock, freq, from, to
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        int financialLimit = 5; // 추후 수정, test용

        Mono<List<Financial>> financialsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                financialRepository.findByStockOrderByReportDateDescVersionDesc(
                                        stock,
                                        PageRequest.of(0, financialLimit)
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        // 5. DB + 외부 데이터 → FastAPI 요청 → 응답 DTO 조립
        return Mono.zip(stockMono, candlesMono, indicatorsMono, financialsMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    List<PriceOhlcv> candles = tuple.getT2();
                    List<IndicatorValue> indicators = tuple.getT3();
                    List<Financial> financials = tuple.getT4();

                    FeatOneRequestDto requestDto = buildFeatOneRequestDto(
                            stock, candles, indicators, financials
                    );

                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(textDto -> {
                                FeatOneResponseDataDto data =
                                        buildFeatOneResponseDto(
                                                stock,
                                                candles,
                                                indicators,
                                                financials,
                                                textDto
                                        );

                                boolean chartUnavailable = candles == null || candles.isEmpty();

                                return new FeatOneResult(data, chartUnavailable);
                            });
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
        String marketDivCode = toKisMarketDivCode(stock.getExchange()); // KOSPI/KOSDAQ/KONEX/해외

        // 1) 먼저 DB 조회
        return Mono.fromCallable(() ->
                        priceOhlcvRepository.findByStockCodeAndFreqAndTsBetween(
                                stockCode, freq, from, to
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(existing -> {
                    // 1-1) 이미 DB에 있으면 그대로 반환
                    if (!existing.isEmpty()) {
                        return Mono.just(existing);
                    }

                    // 2) DB에 없으면 KIS 캔들 호출
                    Mono<List<PriceOhlcvDto>> fromKis =
                            krStockClient.fetchCandles(stockCode, marketDivCode, freq, from, to);

                    // 3) KIS 실패 시 Global(Marketstack) 폴백
                    Mono<List<PriceOhlcvDto>> fromGlobal =
                            fromKis.onErrorResume(ex ->
                                    globalStockClient.fetchCandles(stockCode, freq, from, to)
                            );

                    // 4) 외부에서 가져온 DTO를 엔티티로 변환 후 saveAll
                    return fromGlobal.flatMap(dtoList -> {
                        if (dtoList == null || dtoList.isEmpty()) {
                            // 외부에서도 아무것도 못 가져온 경우 → 빈 리스트
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
        entity.setStock(stock); // ManyToOne
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
            h = f.getPeriodNo(); // 1 or 2
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

                // 규모
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

                // 지표(Double)
                .operatingMargin(bdToDouble(f.getOperatingMargin()))
                .netMargin(bdToDouble(f.getNetMargin()))
                .roe(bdToDouble(f.getRoe()))
                .per(bdToDouble(f.getPer()))
                .pbr(bdToDouble(f.getPbr()))
                .debtRatio(debtRatio)

                .build();
    }

    //Stock 엔티티 → StockDto 매핑
    private StockDto toStockDto(Stock stock) {
        if (stock == null) return null;

        return StockDto.builder()
                .stockId(stock.getStockId())
                .stockCode(stock.getStockCode())
                .isin(stock.getIsin())
                .companyName(stock.getCompanyName())
                // 필요하면 아래 값들 점점 채워나가면 됨
                .exchangeId(
                        stock.getExchange() != null ? stock.getExchange().getExchangeId() : null
                )
                .exchangeCode(
                        stock.getExchange() != null ? stock.getExchange().getCode() : null
                )
                .assetType(stock.getAssetType())
                .currency(stock.getCurrency())
                .industryId(
                        stock.getIndustry() != null ? stock.getIndustry().getIndustryId() : null
                )
                // price/changeRate는 실시간 조회용이라 여기서는 null로 둬도 됨
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
                // .options(null) // 필요하면 나중에 추가
                .build();
    }

    private FeatOneResponseDataDto buildFeatOneResponseDto(
            Stock stock,
            List<PriceOhlcv> candles,
            List<IndicatorValue> indicators,
            List<Financial> financials,
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
                // Text 섹션 전체를 analysis에 그대로 넣는다
                .analysis(textDto)
                .build();
    }

    // 거래소 → KIS marketDivCode 매핑
    private String toKisMarketDivCode(Exchange exchange) {
        return switch (exchange.getCode()) {
            case "KOSPI" -> "J";
            case "KOSDAQ" -> "Q";
            case "KONEX" -> "K";
            default -> "B";  // 해외 기타 거래소 전부 B
        };
    }

    // BigDecimal → Double 변환
    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

}
