package com.qaima.service.feature2;

import com.qaima.domain.BaseRate;
import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.ShortSelling;
import com.qaima.domain.Stock;
import com.qaima.domain.StockInvestorFlow;
import com.qaima.domain.MarketInvestorFlow;
import com.qaima.dto.feature2.Feature2InvestorFlowDto;
import com.qaima.dto.feature2.Feature2MacroRatesDto;
import com.qaima.dto.feature2.Feature2MacroRatesSeriesDto;
import com.qaima.dto.feature2.Feature2BaseRateSeriesPointDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.feature2.Feature2RelatedStockCardDto;
import com.qaima.dto.feature2.Feature2ShortSellingSeriesPointDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.external.KisInvestorFlowClient;
import com.qaima.repository.BaseRateRepository;
import com.qaima.repository.MarketInvestorFlowRepository;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.repository.ShortSellingRepository;
import com.qaima.repository.StockRepository;
import com.qaima.repository.StockInvestorFlowRepository;
import com.qaima.service.baserate.BaseRateSyncService;
import com.qaima.service.baserate.FredBaseRateSyncService;
import com.qaima.service.bondyield.BondYieldInstrument;
import com.qaima.service.bondyield.BondYieldSyncService;
import com.qaima.service.bondyield.FredBondYieldInstrument;
import com.qaima.service.bondyield.FredBondYieldSyncService;
import com.qaima.service.exchangerate.ExchangeRateInstrument;
import com.qaima.service.exchangerate.ExchangeRateSyncService;
import com.qaima.service.feature2.model.Feature2IndustryContext;
import com.qaima.service.feature2.model.Feature2StockContext;
import com.qaima.service.feature2.resolver.Feature2IndustryReader;
import com.qaima.service.feature2.resolver.Feature2StockResolver;
import com.qaima.service.marketdata.reader.PriceSnapshotReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature2CardService {

    private final BaseRateFeatureService baseRateFeatureService;
    private final ShortSellingFeatureService shortSellingFeatureService;
    private final Feature2StockResolver stockResolver;
    private final ShortSellingRepository shortSellingRepository;
    private final BaseRateRepository baseRateRepository;
    private final Feature2IndustryReader feature2IndustryReader;
    private final IndustryIndexService industryIndexService;
    private final StockRepository stockRepository;
    private final PriceOhlcvRepository priceOhlcvRepository;
    private final StockInvestorFlowRepository stockInvestorFlowRepository;
    private final MarketInvestorFlowRepository marketInvestorFlowRepository;
    private final PriceSnapshotReader priceSnapshotReader;
    private final BaseRateSyncService baseRateSyncService;
    private final FredBaseRateSyncService fredBaseRateSyncService;
    private final ExchangeRateSyncService exchangeRateSyncService;
    private final BondYieldSyncService bondYieldSyncService;
    private final FredBondYieldSyncService fredBondYieldSyncService;

    private static final int RELATED_FILTER_WINDOW = 60;
    private static final int MIN_RELATED_FILTER_COUNT = 5;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    public Mono<CardResult<Feature2MetricsDto.BaseRateMetrics>> loadBaseRate() {
        Feature2MetaDto meta = Feature2MetaDto.empty();

        return baseRateFeatureService.loadLatest(meta)
                .map(data -> CardResult.of(data, meta))
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.of(null, meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] baseRate card load failed. cause={}", ex.getMessage(), ex);
                    return Mono.just(CardResult.of(null, meta));
                });
    }

    public Mono<CardResult<List<Feature2BaseRateSeriesPointDto>>> loadBaseRateSeries(int limit) {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        int safeLimit = Math.max(1, Math.min(limit, 1095));

        return baseRateFeatureService.loadLatest(meta)
                .then(Mono.<List<BaseRate>>fromCallable(() -> baseRateRepository.findByStatCodeAndItemCodeAndCycleOrderByBaseDateDesc(
                                BaseRateSyncService.DEFAULT_STAT_CODE,
                                BaseRateSyncService.DEFAULT_ITEM_CODE,
                                BaseRateSyncService.DEFAULT_CYCLE,
                                PageRequest.of(0, safeLimit)
                        ))
                        .subscribeOn(Schedulers.boundedElastic()))
                .map(this::toBaseRateSeriesPoints)
                .map(points -> CardResult.<List<Feature2BaseRateSeriesPointDto>>of(points, meta))
                .switchIfEmpty(emptyBaseRateSeries(meta))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] baseRate series load failed. cause={}", ex.getMessage(), ex);
                    return emptyBaseRateSeries(meta);
                });
    }

    public Mono<CardResult<Feature2MacroRatesDto>> loadMacroRates() {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        LocalDate today = LocalDate.now(SEOUL);

        Mono<Optional<Feature2MacroRatesDto.RatePoint>> krBaseRate = baseRateSyncService.ensureDailySynced(today)
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] KR base rate sync failed. cause={}", ex.getMessage());
                    return baseRateSyncService.findLatest(today);
                })
                .map(row -> Feature2MacroRatesDto.RatePoint.builder()
                        .date(row.getBaseDate())
                        .value(row.getRateValue())
                        .unit(row.getUnitName())
                        .source(row.getSource())
                        .build())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        Mono<Optional<Feature2MacroRatesDto.RatePoint>> usFedFunds = fredBaseRateSyncService.ensureSynced(today)
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] US fed funds sync failed. cause={}", ex.getMessage());
                    return fredBaseRateSyncService.findLatest(today);
                })
                .map(row -> Feature2MacroRatesDto.RatePoint.builder()
                        .date(row.getBaseDate())
                        .value(row.getRateValue())
                        .unit(row.getUnitName())
                        .source(row.getSource())
                        .build())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        Mono<Optional<Feature2MacroRatesDto.ExchangeRatePoint>> usdKrw = exchangeRateSyncService
                .ensureDailySynced(ExchangeRateInstrument.USD_KRW, today)
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] USD/KRW sync failed. cause={}", ex.getMessage());
                    return exchangeRateSyncService.findLatest(ExchangeRateInstrument.USD_KRW, today);
                })
                .map(row -> Feature2MacroRatesDto.ExchangeRatePoint.builder()
                        .date(row.getRateDate())
                        .pairCode(row.getPairCode())
                        .baseCurrency(row.getBaseCurrency())
                        .quoteCurrency(row.getQuoteCurrency())
                        .value(row.getRateValue())
                        .unit(row.getUnitName())
                        .source(row.getSource())
                        .build())
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty());

        Mono<List<Feature2MacroRatesDto.BondYieldPoint>> krBondYields = reactor.core.publisher.Flux
                .fromArray(BondYieldInstrument.values())
                .concatMap(instrument -> bondYieldSyncService.ensureDailySynced(instrument, today)
                        .onErrorResume(ex -> {
                            log.warn("[Feature2CardService] KR bond sync failed. instrument={}, cause={}",
                                    instrument.instrumentCode(), ex.getMessage());
                            return bondYieldSyncService.findLatest(instrument, today);
                        }))
                .map(row -> Feature2MacroRatesDto.BondYieldPoint.builder()
                        .date(row.getYieldDate())
                        .countryCode(row.getCountryCode())
                        .instrumentCode(row.getInstrumentCode())
                        .instrumentName(row.getInstrumentName())
                        .maturityMonths(row.getMaturityMonths())
                        .value(row.getYieldValue())
                        .unit(row.getUnitName())
                        .source(row.getSource())
                        .build())
                .collectList();

        Mono<List<Feature2MacroRatesDto.BondYieldPoint>> usBondYields = reactor.core.publisher.Flux
                .fromArray(FredBondYieldInstrument.values())
                .concatMap(instrument -> fredBondYieldSyncService.ensureMonthlySynced(instrument, today)
                        .onErrorResume(ex -> {
                            log.warn("[Feature2CardService] US bond sync failed. instrument={}, cause={}",
                                    instrument.instrumentCode(), ex.getMessage());
                            return fredBondYieldSyncService.findLatest(instrument, today);
                        }))
                .map(row -> Feature2MacroRatesDto.BondYieldPoint.builder()
                        .date(row.getYieldDate())
                        .countryCode(row.getCountryCode())
                        .instrumentCode(row.getInstrumentCode())
                        .instrumentName(row.getInstrumentName())
                        .maturityMonths(row.getMaturityMonths())
                        .value(row.getYieldValue())
                        .unit(row.getUnitName())
                        .source(row.getSource())
                        .build())
                .collectList();

        return Mono.zip(krBaseRate, usFedFunds, usdKrw, krBondYields, usBondYields)
                .map(tuple -> {
                    List<Feature2MacroRatesDto.BondYieldPoint> bondYields = new ArrayList<>();
                    bondYields.addAll(tuple.getT4());
                    bondYields.addAll(tuple.getT5());
                    bondYields.sort(Comparator
                            .comparing(Feature2MacroRatesDto.BondYieldPoint::getCountryCode)
                            .thenComparing(Feature2MacroRatesDto.BondYieldPoint::getMaturityMonths));
                    return Feature2MacroRatesDto.builder()
                            .krBaseRate(tuple.getT1().orElse(null))
                            .usFedFundsRate(tuple.getT2().orElse(null))
                            .usdKrw(tuple.getT3().orElse(null))
                            .bondYields(bondYields)
                            .build();
                })
                .map(data -> CardResult.of(data, meta))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] macro rates card load failed. cause={}", ex.getMessage(), ex);
                    return Mono.just(CardResult.of(Feature2MacroRatesDto.builder()
                            .bondYields(List.of())
                            .build(), meta));
                });
    }

    public Mono<CardResult<Feature2MacroRatesSeriesDto>> loadMacroRatesSeries(int limit) {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        LocalDate today = LocalDate.now(SEOUL);
        int safeLimit = Math.max(5, Math.min(limit, 500));

        Mono<List<Feature2MacroRatesSeriesDto.SeriesBlock>> baseRateSeries = baseRateSyncService.ensureDailySynced(today)
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] KR base rate series sync failed. cause={}", ex.getMessage());
                    return baseRateSyncService.findLatest(today);
                })
                .then(Mono.fromCallable(() -> baseRateRepository.findByStatCodeAndItemCodeAndCycleOrderByBaseDateDesc(
                                BaseRateSyncService.DEFAULT_STAT_CODE,
                                BaseRateSyncService.DEFAULT_ITEM_CODE,
                                BaseRateSyncService.DEFAULT_CYCLE,
                                PageRequest.of(0, safeLimit)
                        ))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(rows -> List.of(toSeriesBlock(
                                "KR_BASE_RATE",
                                "한국 기준금리",
                                "macro",
                                rows.isEmpty() ? "%" : rows.get(0).getUnitName(),
                                rows.stream()
                                        .sorted(Comparator.comparing(entity -> entity.getBaseDate()))
                                        .map(entity -> Feature2MacroRatesSeriesDto.Point.builder()
                                                .date(entity.getBaseDate())
                                                .value(entity.getRateValue())
                                                .build())
                                        .toList()
                        ))))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] KR base rate series load failed. cause={}", ex.getMessage());
                    return Mono.just(List.of());
                });

        Mono<List<Feature2MacroRatesSeriesDto.SeriesBlock>> usFedFundsSeries = fredBaseRateSyncService.ensureSynced(today)
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] US fed funds series sync failed. cause={}", ex.getMessage());
                    return fredBaseRateSyncService.findLatest(today);
                })
                .then(fredBaseRateSyncService.findLatestRows(safeLimit))
                .map(rows -> List.of(toSeriesBlock(
                        "US_FED_FUNDS",
                        "미국 기준금리",
                        "macro",
                        rows.isEmpty() ? "%" : rows.get(0).getUnitName(),
                        rows.stream()
                                .sorted(Comparator.comparing(entity -> entity.getBaseDate()))
                                .map(entity -> Feature2MacroRatesSeriesDto.Point.builder()
                                        .date(entity.getBaseDate())
                                        .value(entity.getRateValue())
                                        .build())
                                .toList()
                )))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] US fed funds series load failed. cause={}", ex.getMessage());
                    return Mono.just(List.of());
                });

        Mono<List<Feature2MacroRatesSeriesDto.SeriesBlock>> exchangeRateSeries = exchangeRateSyncService
                .ensureDailySynced(ExchangeRateInstrument.USD_KRW, today)
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] exchange rate series sync failed. cause={}", ex.getMessage());
                    return exchangeRateSyncService.findLatest(ExchangeRateInstrument.USD_KRW, today);
                })
                .then(exchangeRateSyncService.findLatestRows(ExchangeRateInstrument.USD_KRW, safeLimit))
                .map(rows -> List.of(toSeriesBlock(
                        "USD_KRW",
                        "USD/KRW",
                        "macro",
                        rows.isEmpty() ? "원" : rows.get(0).getUnitName(),
                        rows.stream()
                                .sorted(Comparator.comparing(entity -> entity.getRateDate()))
                                .map(entity -> Feature2MacroRatesSeriesDto.Point.builder()
                                        .date(entity.getRateDate())
                                        .value(entity.getRateValue())
                                        .build())
                                .toList()
                )))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] exchange rate series load failed. cause={}", ex.getMessage());
                    return Mono.just(List.of());
                });

        Mono<List<Feature2MacroRatesSeriesDto.SeriesBlock>> krBondSeries = reactor.core.publisher.Flux
                .fromArray(BondYieldInstrument.values())
                .concatMap(instrument -> bondYieldSyncService.ensureDailySynced(instrument, today)
                        .onErrorResume(ex -> {
                            log.warn("[Feature2CardService] KR bond series sync failed. instrument={}, cause={}",
                                    instrument.instrumentCode(), ex.getMessage());
                            return bondYieldSyncService.findLatest(instrument, today);
                        })
                        .then(bondYieldSyncService.findLatestRows(instrument, safeLimit))
                        .map(rows -> toSeriesBlock(
                                instrument.instrumentCode(),
                                instrument.instrumentName(),
                                "macro",
                                rows.isEmpty() ? "%" : rows.get(0).getUnitName(),
                                rows.stream()
                                        .sorted(Comparator.comparing(entity -> entity.getYieldDate()))
                                        .map(entity -> Feature2MacroRatesSeriesDto.Point.builder()
                                                .date(entity.getYieldDate())
                                                .value(entity.getYieldValue())
                                                .build())
                                        .toList()
                        ))
                        .onErrorResume(ex -> Mono.empty()))
                .collectList();

        Mono<List<Feature2MacroRatesSeriesDto.SeriesBlock>> usBondSeries = reactor.core.publisher.Flux
                .fromArray(FredBondYieldInstrument.values())
                .concatMap(instrument -> fredBondYieldSyncService.ensureMonthlySynced(instrument, today)
                        .onErrorResume(ex -> {
                            log.warn("[Feature2CardService] US bond series sync failed. instrument={}, cause={}",
                                    instrument.instrumentCode(), ex.getMessage());
                            return fredBondYieldSyncService.findLatest(instrument, today);
                        })
                        .then(fredBondYieldSyncService.findLatestRows(instrument, safeLimit))
                        .map(rows -> toSeriesBlock(
                                instrument.instrumentCode(),
                                instrument.instrumentName(),
                                "macro",
                                rows.isEmpty() ? "%" : rows.get(0).getUnitName(),
                                rows.stream()
                                        .sorted(Comparator.comparing(entity -> entity.getYieldDate()))
                                        .map(entity -> Feature2MacroRatesSeriesDto.Point.builder()
                                                .date(entity.getYieldDate())
                                                .value(entity.getYieldValue())
                                                .build())
                                        .toList()
                        ))
                        .onErrorResume(ex -> Mono.empty()))
                .collectList();

        return Mono.zip(baseRateSeries, usFedFundsSeries, exchangeRateSeries, krBondSeries, usBondSeries)
                .map(tuple -> {
                    List<Feature2MacroRatesSeriesDto.SeriesBlock> series = new ArrayList<>();
                    series.addAll(tuple.getT1());
                    series.addAll(tuple.getT2());
                    series.addAll(tuple.getT3());
                    series.addAll(tuple.getT4());
                    series.addAll(tuple.getT5());
                    return CardResult.of(Feature2MacroRatesSeriesDto.builder()
                            .series(series)
                            .build(), meta);
                })
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] macro rates series load failed. cause={}", ex.getMessage(), ex);
                    return Mono.just(CardResult.of(Feature2MacroRatesSeriesDto.builder()
                            .series(List.of())
                            .build(), meta));
                });
    }

    public Mono<CardResult<Feature2MetricsDto.ShortSellingMetrics>> loadShortSelling(String stockCode) {
        Feature2MetaDto meta = Feature2MetaDto.empty();

        if (stockCode == null || stockCode.isBlank()) {
            return Mono.just(CardResult.of(null, meta));
        }

        return stockResolver.resolve(stockCode, meta)
                .flatMap(stockContextOpt -> toShortSellingResult(stockContextOpt, meta))
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.of(null, meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] shortSelling card load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return Mono.just(CardResult.of(null, meta));
                });
    }

    public Mono<CardResult<IndustryIndexBlockDto>> loadIndustryIndex(
            String stockCode,
            Freq freq,
            Integer window
    ) {
        Feature2MetaDto meta = Feature2MetaDto.empty();

        if (stockCode == null || stockCode.isBlank()) {
            return Mono.just(CardResult.of(null, meta));
        }

        Freq safeFreq = freq == null ? Freq.ONE_D : freq;
        int safeWindow = window == null || window <= 0 ? 120 : window;

        return stockResolver.resolve(stockCode, meta)
                .flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        return Mono.just(CardResult.<IndustryIndexBlockDto>of(null, meta));
                    }

                    Feature2StockContext stockContext = stockContextOpt.get();
                    return feature2IndustryReader.resolve(stockContext.stock(), meta)
                            .flatMap(industryContextOpt -> toIndustryIndexResult(industryContextOpt, meta, safeFreq, safeWindow));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.<IndustryIndexBlockDto>of(null, meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] industryIndex card load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return Mono.just(CardResult.<IndustryIndexBlockDto>of(null, meta));
                });
    }

    public Mono<CardResult<List<Feature2ShortSellingSeriesPointDto>>> loadShortSellingSeries(
            String stockCode,
            int limit
    ) {
        Feature2MetaDto meta = Feature2MetaDto.empty();

        if (stockCode == null || stockCode.isBlank()) {
            return emptyShortSellingSeries(meta);
        }

        int safeLimit = Math.max(1, Math.min(limit, 252));

        return stockResolver.resolve(stockCode, meta)
                .flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        return emptyShortSellingSeries(meta);
                    }

                    Feature2StockContext stockContext = stockContextOpt.get();
                    return Mono.<List<ShortSelling>>fromCallable(() -> shortSellingRepository.findByStockOrderByReportDateDesc(
                                    stockContext.stock(),
                                    PageRequest.of(0, safeLimit)
                            ))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(this::toShortSellingSeriesPoints)
                            .map(points -> CardResult.<List<Feature2ShortSellingSeriesPointDto>>of(points, meta));
                })
                .switchIfEmpty(emptyShortSellingSeries(meta))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] shortSelling series load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return emptyShortSellingSeries(meta);
                });
    }

    public Mono<CardResult<List<Feature2RelatedStockCardDto>>> loadRelatedStocks(
            String stockCode,
            int limit
    ) {
        Feature2MetaDto meta = Feature2MetaDto.empty();

        if (stockCode == null || stockCode.isBlank()) {
            return Mono.just(CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta));
        }

        int safeLimit = Math.max(1, Math.min(limit, 30));

        return stockResolver.resolve(stockCode, meta)
                .<CardResult<List<Feature2RelatedStockCardDto>>>flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        return emptyRelatedStocks(meta);
                    }

                    Feature2StockContext stockContext = stockContextOpt.get();
                    return feature2IndustryReader.resolve(stockContext.stock(), meta)
                            .<CardResult<List<Feature2RelatedStockCardDto>>>flatMap(industryContextOpt -> {
                                if (industryContextOpt.isEmpty()) {
                                    return emptyRelatedStocks(meta);
                                }

                                Long industryId = industryContextOpt.get().industry().getIndustryId();
                                return Mono.fromCallable(() -> buildCheapRelatedStocks(
                                                stockContext.stock(),
                                                industryId,
                                                safeLimit
                                        ))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .map(data -> CardResult.<List<Feature2RelatedStockCardDto>>of(data, meta));
                            });
                })
                .switchIfEmpty(Mono.<CardResult<List<Feature2RelatedStockCardDto>>>fromSupplier(
                        () -> CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta)
                ))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] relatedStocks card load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return emptyRelatedStocks(meta);
                });
    }

    public Mono<CardResult<Feature2InvestorFlowDto>> loadInvestorFlow(
            String stockCode,
            int limit
    ) {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.just(CardResult.of(null, meta));
        }

        int safeLimit = Math.max(1, Math.min(limit, 252));
        return stockResolver.resolve(stockCode, meta)
                .flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        return Mono.just(CardResult.<Feature2InvestorFlowDto>of(null, meta));
                    }
                    return Mono.fromCallable(() -> buildInvestorFlow(stockContextOpt.get().stock(), safeLimit))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(data -> CardResult.of(data, meta));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.<Feature2InvestorFlowDto>of(null, meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] investorFlow card load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return Mono.just(CardResult.<Feature2InvestorFlowDto>of(null, meta));
                });
    }

    private Mono<CardResult<Feature2MetricsDto.ShortSellingMetrics>> toShortSellingResult(
            Optional<Feature2StockContext> stockContextOpt,
            Feature2MetaDto meta
    ) {
        if (stockContextOpt.isEmpty()) {
            return Mono.just(CardResult.of(null, meta));
        }

        Feature2StockContext stockContext = stockContextOpt.get();
        return shortSellingFeatureService.loadLatest(stockContext.stock(), meta)
                .map(data -> CardResult.of(data, meta))
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.of(null, meta)));
    }

    private Mono<CardResult<List<Feature2BaseRateSeriesPointDto>>> emptyBaseRateSeries(Feature2MetaDto meta) {
        return Mono.just(CardResult.<List<Feature2BaseRateSeriesPointDto>>of(List.of(), meta));
    }

    private List<Feature2BaseRateSeriesPointDto> toBaseRateSeriesPoints(List<BaseRate> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(BaseRate::getBaseDate))
                .map(entity -> Feature2BaseRateSeriesPointDto.builder()
                        .date(entity.getBaseDate())
                        .value(entity.getRateValue())
                        .unit(entity.getUnitName())
                        .build())
                .toList();
    }

    private Mono<CardResult<List<Feature2ShortSellingSeriesPointDto>>> emptyShortSellingSeries(Feature2MetaDto meta) {
        return Mono.just(CardResult.<List<Feature2ShortSellingSeriesPointDto>>of(List.of(), meta));
    }

    private Mono<CardResult<List<Feature2RelatedStockCardDto>>> emptyRelatedStocks(Feature2MetaDto meta) {
        return Mono.just(CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta));
    }

    private List<Feature2ShortSellingSeriesPointDto> toShortSellingSeriesPoints(List<ShortSelling> rows) {
        return rows.stream()
                .sorted(Comparator.comparing(ShortSelling::getReportDate))
                .map(entity -> Feature2ShortSellingSeriesPointDto.builder()
                        .reportDate(entity.getReportDate())
                        .shortVolumeRatio(entity.getShortVolumeRatio())
                        .shortAmountRatio(entity.getShortAmountRatio())
                        .shortVolumeTotal(entity.getShortVolumeTotal())
                        .totalVolume(entity.getTotalVolume())
                        .shortAmountTotal(entity.getShortAmountTotal())
                        .totalAmount(entity.getTotalAmount())
                        .build())
                .toList();
    }

    private Feature2MacroRatesSeriesDto.SeriesBlock toSeriesBlock(
            String key,
            String label,
            String group,
            String unit,
            List<Feature2MacroRatesSeriesDto.Point> points
    ) {
        return Feature2MacroRatesSeriesDto.SeriesBlock.builder()
                .key(key)
                .label(label)
                .group(group)
                .unit(unit)
                .points(points == null ? List.of() : points)
                .build();
    }

    private Mono<CardResult<IndustryIndexBlockDto>> toIndustryIndexResult(
            Optional<Feature2IndustryContext> industryContextOpt,
            Feature2MetaDto meta,
            Freq freq,
            int window
    ) {
        if (industryContextOpt.isEmpty()) {
            return Mono.just(CardResult.of(null, meta));
        }

        Feature2IndustryContext industryContext = industryContextOpt.get();
        return industryIndexService.loadIndustryIndex(
                        industryContext.industry().getIndustryId(),
                        meta,
                        freq,
                        window
                )
                .map(data -> CardResult.of(data, meta))
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.of(null, meta)));
    }

    private List<Feature2RelatedStockCardDto> buildCheapRelatedStocks(
            Stock anchor,
            Long industryId,
            int limit
    ) {
        List<Stock> stocks = stockRepository.findAllByIndustryIndustryId(industryId);
        if (stocks == null || stocks.isEmpty()) {
            return List.of();
        }

        CheapMetrics anchorMetrics = loadCheapMetrics(anchor);
        List<CheapCandidate> candidates = new ArrayList<>();

        for (Stock stock : stocks) {
            if (stock == null || stock.getStockCode() == null) {
                continue;
            }
            if (stock.getStockCode().equals(anchor.getStockCode())) {
                continue;
            }

            CheapMetrics metrics = loadCheapMetrics(stock);
            if (metrics == null) {
                continue;
            }
            candidates.add(new CheapCandidate(stock, metrics));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        if (candidates.size() < MIN_RELATED_FILTER_COUNT) {
            return candidates.stream()
                    .sorted(Comparator.comparing(candidate -> distanceScore(candidate.metrics(), anchorMetrics)))
                    .limit(limit)
                    .map(candidate -> mapRelatedStockCard(candidate.stock(), candidate.metrics()))
                    .toList();
        }

        FilterRange primaryRange = primaryRange(candidates.size());

        List<CheapCandidate> filtered = candidates.stream()
                .filter(candidate -> matchesRange(candidate.metrics(), anchorMetrics, primaryRange))
                .sorted(Comparator.comparing(candidate -> distanceScore(candidate.metrics(), anchorMetrics)))
                .limit(limit)
                .toList();

        if (filtered.size() < MIN_RELATED_FILTER_COUNT) {
            filtered = candidates.stream()
                    .filter(candidate -> matchesRange(candidate.metrics(), anchorMetrics, new FilterRange(0.35, 3.0, 0.5, 2.0)))
                    .sorted(Comparator.comparing(candidate -> distanceScore(candidate.metrics(), anchorMetrics)))
                    .limit(limit)
                    .toList();
        }

        if (filtered.size() < MIN_RELATED_FILTER_COUNT) {
            filtered = candidates.stream()
                    .sorted(Comparator.comparing(candidate -> distanceScore(candidate.metrics(), anchorMetrics)))
                    .limit(limit)
                    .toList();
        }

        return filtered.stream()
                .map(candidate -> mapRelatedStockCard(candidate.stock(), candidate.metrics()))
                .toList();
    }

    private CheapMetrics loadCheapMetrics(Stock stock) {
        List<PriceOhlcv> rows = priceOhlcvRepository.findBefore(
                stock.getStockCode(),
                Freq.ONE_D,
                OffsetDateTime.now(),
                PageRequest.of(0, RELATED_FILTER_WINDOW)
        );

        if (rows == null || rows.size() < 2) {
            return null;
        }

        BigDecimal turnoverSum = BigDecimal.ZERO;
        int turnoverCount = 0;
        List<BigDecimal> orderedCloses = new ArrayList<>(rows.size());
        BigDecimal latestPrice = null;
        BigDecimal prevPrice = null;
        BigDecimal latestVolume = null;

        for (int i = 0; i < rows.size(); i++) {
            PriceOhlcv row = rows.get(i);
            if (i == 0) {
                latestPrice = row.getClose();
                latestVolume = row.getVolume();
            } else if (i == 1) {
                prevPrice = row.getClose();
            }

            if (row.getClose() != null && row.getVolume() != null) {
                turnoverSum = turnoverSum.add(row.getClose().multiply(row.getVolume()));
                turnoverCount++;
            }
            if (row.getClose() != null) {
                orderedCloses.add(0, row.getClose());
            }
        }

        if (orderedCloses.size() < 2) {
            return null;
        }

        BigDecimal avgTurnover = turnoverCount > 0
                ? turnoverSum.divide(BigDecimal.valueOf(turnoverCount), 6, RoundingMode.HALF_UP)
                : null;
        BigDecimal volatility = computeVolatility(orderedCloses);

        return new CheapMetrics(latestPrice, prevPrice, latestVolume, avgTurnover, volatility);
    }

    private BigDecimal computeVolatility(List<BigDecimal> closes) {
        if (closes == null || closes.size() < 2) {
            return null;
        }

        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            BigDecimal prev = closes.get(i - 1);
            BigDecimal cur = closes.get(i);
            if (prev == null || cur == null || prev.compareTo(BigDecimal.ZERO) <= 0 || cur.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            returns.add(Math.log(cur.doubleValue() / prev.doubleValue()));
        }

        if (returns.size() < 2) {
            return null;
        }

        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = returns.stream()
                .mapToDouble(value -> Math.pow(value - mean, 2))
                .average()
                .orElse(0.0);
        return BigDecimal.valueOf(Math.sqrt(variance));
    }

    private FilterRange primaryRange(int candidateCount) {
        if (candidateCount > 30) {
            return new FilterRange(0.7, 1.3, 0.7, 1.3);
        }
        if (candidateCount >= 20) {
            return new FilterRange(0.6, 1.6, 0.7, 1.4);
        }
        return new FilterRange(0.4, 2.5, 0.6, 1.7);
    }

    private boolean matchesRange(CheapMetrics candidate, CheapMetrics anchor, FilterRange range) {
        return withinRatio(candidate.avgTurnover(), anchor.avgTurnover(), range.turnLow(), range.turnHigh())
                && withinRatio(candidate.volatility(), anchor.volatility(), range.volLow(), range.volHigh());
    }

    private boolean withinRatio(BigDecimal value, BigDecimal anchor, double low, double high) {
        if (value == null || anchor == null || anchor.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        BigDecimal ratio = value.divide(anchor, MathContext.DECIMAL64);
        return ratio.compareTo(BigDecimal.valueOf(low)) >= 0
                && ratio.compareTo(BigDecimal.valueOf(high)) <= 0;
    }

    private double distanceScore(CheapMetrics candidate, CheapMetrics anchor) {
        double turnoverGap = ratioGap(candidate.avgTurnover(), anchor.avgTurnover());
        double volatilityGap = ratioGap(candidate.volatility(), anchor.volatility());
        return turnoverGap * 0.65 + volatilityGap * 0.35;
    }

    private Feature2InvestorFlowDto buildInvestorFlow(Stock stock, int limit) {
        if (stock == null) {
            return null;
        }

        List<StockInvestorFlow> stockRows = stockInvestorFlowRepository.findByStockOrderByTradeDateDesc(
                        stock,
                        PageRequest.of(0, limit)
                )
                .stream()
                .sorted(Comparator.comparing(StockInvestorFlow::getTradeDate))
                .toList();

        String marketCode = toInvestorMarketCode(stock);
        String marketIndustryCode = marketCode == null ? null : KisInvestorFlowClient.defaultMarketIndustryCode(marketCode);
        List<MarketInvestorFlow> marketRows = marketCode == null
                ? List.of()
                : marketInvestorFlowRepository.findByMarketCodeAndIndustryCodeOrderByTradeDateDesc(
                                marketCode,
                                marketIndustryCode,
                                PageRequest.of(0, limit)
                        )
                        .stream()
                        .sorted(Comparator.comparing(MarketInvestorFlow::getTradeDate))
                        .toList();

        return Feature2InvestorFlowDto.builder()
                .stockCode(stock.getStockCode())
                .marketCode(marketCode)
                .stockSummary(buildStockInvestorFlowSummary(limit, stockRows))
                .marketSummary(buildMarketInvestorFlowSummary(limit, marketRows))
                .stockSeries(stockRows.stream().map(this::toStockInvestorFlowPoint).toList())
                .marketSeries(marketRows.stream().map(this::toMarketInvestorFlowPoint).toList())
                .build();
    }

    private String toInvestorMarketCode(Stock stock) {
        if (stock == null || stock.getExchange() == null || stock.getExchange().getCode() == null) {
            return null;
        }
        return switch (stock.getExchange().getCode().toUpperCase()) {
            case "KOSPI", "KSP" -> "KSP";
            case "KOSDAQ", "KSQ" -> "KSQ";
            default -> null;
        };
    }

    private Feature2InvestorFlowDto.InvestorFlowSummary buildStockInvestorFlowSummary(
            int window,
            List<StockInvestorFlow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        BigDecimal foreignValue = rows.stream()
                .map(StockInvestorFlow::getForeignNetBuyValueMillion)
                .reduce(BigDecimal.ZERO, this::addNullable);
        BigDecimal institutionValue = rows.stream()
                .map(StockInvestorFlow::getInstitutionNetBuyValueMillion)
                .reduce(BigDecimal.ZERO, this::addNullable);
        BigDecimal foreignQty = rows.stream()
                .map(StockInvestorFlow::getForeignNetBuyQty)
                .reduce(BigDecimal.ZERO, this::addNullable);
        BigDecimal institutionQty = rows.stream()
                .map(StockInvestorFlow::getInstitutionNetBuyQty)
                .reduce(BigDecimal.ZERO, this::addNullable);

        return buildInvestorFlowSummary(
                window,
                rows.size(),
                rows.get(0).getTradeDate(),
                rows.get(rows.size() - 1).getTradeDate(),
                foreignValue,
                institutionValue,
                foreignQty,
                institutionQty
        );
    }

    private Feature2InvestorFlowDto.InvestorFlowSummary buildMarketInvestorFlowSummary(
            int window,
            List<MarketInvestorFlow> rows
    ) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }

        BigDecimal foreignValue = rows.stream()
                .map(MarketInvestorFlow::getForeignNetBuyValueMillion)
                .reduce(BigDecimal.ZERO, this::addNullable);
        BigDecimal institutionValue = rows.stream()
                .map(MarketInvestorFlow::getInstitutionNetBuyValueMillion)
                .reduce(BigDecimal.ZERO, this::addNullable);
        BigDecimal foreignQty = rows.stream()
                .map(MarketInvestorFlow::getForeignNetBuyQty)
                .reduce(BigDecimal.ZERO, this::addNullable);
        BigDecimal institutionQty = rows.stream()
                .map(MarketInvestorFlow::getInstitutionNetBuyQty)
                .reduce(BigDecimal.ZERO, this::addNullable);

        return buildInvestorFlowSummary(
                window,
                rows.size(),
                rows.get(0).getTradeDate(),
                rows.get(rows.size() - 1).getTradeDate(),
                foreignValue,
                institutionValue,
                foreignQty,
                institutionQty
        );
    }

    private Feature2InvestorFlowDto.InvestorFlowSummary buildInvestorFlowSummary(
            int window,
            int pointCount,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            BigDecimal foreignValue,
            BigDecimal institutionValue,
            BigDecimal foreignQty,
            BigDecimal institutionQty
    ) {
        BigDecimal combinedValue = addNullable(foreignValue, institutionValue);
        BigDecimal combinedQty = addNullable(foreignQty, institutionQty);
        return Feature2InvestorFlowDto.InvestorFlowSummary.builder()
                .window(window)
                .pointCount(pointCount)
                .startDate(startDate)
                .endDate(endDate)
                .foreignNetBuyValueMillionSum(foreignValue)
                .institutionNetBuyValueMillionSum(institutionValue)
                .combinedNetBuyValueMillionSum(combinedValue)
                .foreignNetBuyQtySum(foreignQty)
                .institutionNetBuyQtySum(institutionQty)
                .combinedNetBuyQtySum(combinedQty)
                .direction(toInvestorFlowDirection(foreignValue, institutionValue))
                .build();
    }

    private BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        BigDecimal safeLeft = left == null ? BigDecimal.ZERO : left;
        BigDecimal safeRight = right == null ? BigDecimal.ZERO : right;
        return safeLeft.add(safeRight);
    }

    private String toInvestorFlowDirection(BigDecimal foreignValue, BigDecimal institutionValue) {
        int foreignSign = compareZero(foreignValue);
        int institutionSign = compareZero(institutionValue);
        if (foreignSign > 0 && institutionSign > 0) {
            return "BOTH_NET_BUY";
        }
        if (foreignSign < 0 && institutionSign < 0) {
            return "BOTH_NET_SELL";
        }
        if (foreignSign > 0 && institutionSign < 0) {
            return "FOREIGN_BUY_INSTITUTION_SELL";
        }
        if (foreignSign < 0 && institutionSign > 0) {
            return "FOREIGN_SELL_INSTITUTION_BUY";
        }
        return "MIXED_OR_FLAT";
    }

    private int compareZero(BigDecimal value) {
        return value == null ? 0 : value.compareTo(BigDecimal.ZERO);
    }

    private Feature2InvestorFlowDto.InvestorFlowPoint toStockInvestorFlowPoint(StockInvestorFlow row) {
        return Feature2InvestorFlowDto.InvestorFlowPoint.builder()
                .tradeDate(row.getTradeDate())
                .closePrice(row.getClosePrice())
                .foreignNetBuyQty(row.getForeignNetBuyQty())
                .foreignNetBuyValueMillion(row.getForeignNetBuyValueMillion())
                .institutionNetBuyQty(row.getInstitutionNetBuyQty())
                .institutionNetBuyValueMillion(row.getInstitutionNetBuyValueMillion())
                .individualNetBuyQty(row.getIndividualNetBuyQty())
                .individualNetBuyValueMillion(row.getIndividualNetBuyValueMillion())
                .build();
    }

    private Feature2InvestorFlowDto.InvestorFlowPoint toMarketInvestorFlowPoint(MarketInvestorFlow row) {
        return Feature2InvestorFlowDto.InvestorFlowPoint.builder()
                .tradeDate(row.getTradeDate())
                .foreignNetBuyQty(row.getForeignNetBuyQty())
                .foreignNetBuyValueMillion(row.getForeignNetBuyValueMillion())
                .institutionNetBuyQty(row.getInstitutionNetBuyQty())
                .institutionNetBuyValueMillion(row.getInstitutionNetBuyValueMillion())
                .individualNetBuyQty(row.getIndividualNetBuyQty())
                .individualNetBuyValueMillion(row.getIndividualNetBuyValueMillion())
                .build();
    }

    private double ratioGap(BigDecimal value, BigDecimal anchor) {
        if (value == null || anchor == null || anchor.compareTo(BigDecimal.ZERO) <= 0) {
            return 999.0;
        }
        double ratio = value.divide(anchor, MathContext.DECIMAL64).doubleValue();
        return Math.abs(ratio - 1.0);
    }

    private Feature2RelatedStockCardDto mapRelatedStockCard(Stock stock, CheapMetrics metrics) {
        BigDecimal price = metrics.latestPrice();
        BigDecimal prevPrice = metrics.prevPrice();
        BigDecimal changeRate = null;
        BigDecimal changeAmount = null;

        if (price != null && prevPrice != null) {
            changeAmount = price.subtract(prevPrice);
            if (prevPrice.compareTo(BigDecimal.ZERO) != 0) {
                changeRate = changeAmount
                        .divide(prevPrice, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));
            }
        }

        return Feature2RelatedStockCardDto.builder()
                .stockCode(stock.getStockCode())
                .companyName(stock.getCompanyName())
                .price(price)
                .changeAmount(changeAmount)
                .changeRate(changeRate)
                .volume(metrics.latestVolume())
                .build();
    }

    private record CheapMetrics(
            BigDecimal latestPrice,
            BigDecimal prevPrice,
            BigDecimal latestVolume,
            BigDecimal avgTurnover,
            BigDecimal volatility
    ) {
    }

    private record CheapCandidate(
            Stock stock,
            CheapMetrics metrics
    ) {
    }

    private record FilterRange(
            double turnLow,
            double turnHigh,
            double volLow,
            double volHigh
    ) {
    }

    public record CardResult<T>(
            T data,
            Feature2MetaDto meta
    ) {
        public static <T> CardResult<T> of(T data, Feature2MetaDto meta) {
            return new CardResult<>(data, meta);
        }
    }
}
