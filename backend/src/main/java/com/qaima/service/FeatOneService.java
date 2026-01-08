package com.qaima.service;

import com.qaima.domain.*;
import com.qaima.dto.*;
import com.qaima.external.AnalysisApiClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.IndicatorValueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FeatOneService {

    private final StockService stockService;
    private final CandleLoadService candleLoadService;

    private final IndicatorValueRepository indicatorValueRepository;
    private final FinancialRepository financialRepository;

    private final AnalysisApiClient analysisApiClient;

    /**
     * 기능 1 전체 플로우
     * 1) 종목 조회 (없으면 외부 메타로 생성)
     * 2) 캔들 로딩 (DB → 없으면 KIS → 실패 시 Global) + source 추적
     * 3) 지표, 재무 로딩 (현재는 DB)
     * 4) FastAPI 분석 요청
     * 5) (data + chartUnavailable) 반환
     *
     * NOTE:
     * - ApiResponse/meta.warning 주입은 Controller 책임
     */
    public Mono<FeatOneResult> getFeatOneData(
            String stockCode,
            Freq freq,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
        // 1) 종목 조회 + 없으면 생성 (한 번만)
        Mono<Stock> stockMono = stockService.getOrCreateStockByCode(stockCode).cache();

        // 2) 캔들 로딩 (fallback/timeout/source는 CandleLoadService가 책임)
        Mono<CandleLoadResult> candleResultMono = stockMono.flatMap(stock ->
                candleLoadService.load(stock, freq, from, to)
        );

        // 3) 지표
        Mono<List<IndicatorValue>> indicatorsMono = stockMono.flatMap(stock ->
                Mono.fromCallable(() ->
                                indicatorValueRepository.findByStockAndFreqAndTsBetweenOrderByTs(
                                        stock, freq, from, to
                                )
                        )
                        .subscribeOn(Schedulers.boundedElastic())
        );

        // 4) 재무 (최근 N개)
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

        // 5) FastAPI 요청 + 최종 조립
        return Mono.zip(stockMono, candleResultMono, indicatorsMono, financialsMono)
                .flatMap(tuple -> {
                    Stock stock = tuple.getT1();
                    CandleLoadResult candleResult = tuple.getT2();
                    List<IndicatorValue> indicators = tuple.getT3();
                    List<Financial> financials = tuple.getT4();

                    // 엔티티 → DTO 변환
                    List<PriceOhlcvDto> candleDtos = candleResult.getCandles().stream()
                            .map(this::toPriceOhlcvDto)
                            .toList();

                    List<IndicatorValueDto> indicatorDtos = indicators.stream()
                            .map(this::toIndicatorValueDto)
                            .toList();

                    List<FinancialSummaryDto> financialDtos = financials.stream()
                            .map(this::toFinancialSummaryDto)
                            .toList();

                    StockDto stockDto = toStockDto(stock);

                    FeatOneRequestDto requestDto = FeatOneRequestDto.builder()
                            .stock(stockDto)
                            .candles(candleDtos)
                            .indicators(indicatorDtos)
                            .financials(financialDtos)
                            .build();

                    // 분석 요청
                    return analysisApiClient.requestStockAnalysis(requestDto)
                            .map(textDto -> {
                                FeatOneResponseDataDto data = FeatOneResponseDataDto.builder()
                                        .stock(stockDto)
                                        .candles(candleDtos)
                                        .indicators(indicatorDtos)
                                        .financials(financialDtos)
                                        .analysis(textDto)
                                        .build();

                                boolean chartUnavailable =
                                        candleResult.getSource() == CandleSource.EMPTY;

                                return new FeatOneResult(data, chartUnavailable);
                            });
                });
    }

    // ===== 매핑 로직 =====

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
        return FinancialSummaryDto.builder()
                .fiscalYear(f.getFiscalYear())
                .fiscalQuarter(f.getFiscalQuarter())
                .reportDate(f.getReportDate())
                .revenue(f.getRevenue())
                .operatingIncome(f.getOperatingIncome())
                .netIncome(f.getNetIncome())
                .assets(f.getAssets())
                .equity(f.getEquity())
                .liabilities(f.getLiabilities())
                .roe(f.getRoe())
                .per(f.getPer())
                .pbr(f.getPbr())
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
}
