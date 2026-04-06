package com.qaima.service.feature2;

import com.qaima.domain.Freq;
import com.qaima.domain.PriceOhlcv;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2BaseRateSeriesPointDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.feature2.Feature2RelatedStockCardDto;
import com.qaima.dto.feature2.Feature2ShortSellingSeriesPointDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.repository.BaseRateRepository;
import com.qaima.repository.PriceOhlcvRepository;
import com.qaima.repository.ShortSellingRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.baserate.BaseRateSyncService;
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
import java.time.OffsetDateTime;
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
    private final PriceSnapshotReader priceSnapshotReader;

    private static final int RELATED_FILTER_WINDOW = 60;
    private static final int MIN_RELATED_FILTER_COUNT = 5;

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
                .flatMap(ignored -> Mono.fromCallable(() -> baseRateRepository.findByStatCodeAndItemCodeAndCycleOrderByBaseDateDesc(
                                BaseRateSyncService.DEFAULT_STAT_CODE,
                                BaseRateSyncService.DEFAULT_ITEM_CODE,
                                BaseRateSyncService.DEFAULT_CYCLE,
                                PageRequest.of(0, safeLimit)
                        ))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(rows -> rows.stream()
                                .sorted(Comparator.comparing(entity -> entity.getBaseDate()))
                                .map(entity -> Feature2BaseRateSeriesPointDto.builder()
                                        .date(entity.getBaseDate())
                                        .value(entity.getRateValue())
                                        .unit(entity.getUnitName())
                                        .build())
                                .toList())
                        .map(points -> CardResult.of(points, meta)))
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.<List<Feature2BaseRateSeriesPointDto>>of(List.of(), meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] baseRate series load failed. cause={}", ex.getMessage(), ex);
                    return Mono.just(CardResult.<List<Feature2BaseRateSeriesPointDto>>of(List.of(), meta));
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
            return Mono.just(CardResult.<List<Feature2ShortSellingSeriesPointDto>>of(List.of(), meta));
        }

        int safeLimit = Math.max(1, Math.min(limit, 252));

        return stockResolver.resolve(stockCode, meta)
                .flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        return Mono.just(CardResult.<List<Feature2ShortSellingSeriesPointDto>>of(List.of(), meta));
                    }

                    Feature2StockContext stockContext = stockContextOpt.get();
                    return Mono.fromCallable(() -> shortSellingRepository.findByStockOrderByReportDateDesc(
                                    stockContext.stock(),
                                    PageRequest.of(0, safeLimit)
                            ))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(rows -> rows.stream()
                                    .sorted(Comparator.comparing(entity -> entity.getReportDate()))
                                    .map(entity -> Feature2ShortSellingSeriesPointDto.builder()
                                            .reportDate(entity.getReportDate())
                                            .shortVolumeRatio(entity.getShortVolumeRatio())
                                            .shortAmountRatio(entity.getShortAmountRatio())
                                            .shortVolumeTotal(entity.getShortVolumeTotal())
                                            .totalVolume(entity.getTotalVolume())
                                            .shortAmountTotal(entity.getShortAmountTotal())
                                            .totalAmount(entity.getTotalAmount())
                                            .build())
                                    .toList())
                            .map(points -> CardResult.of(points, meta));
                })
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.<List<Feature2ShortSellingSeriesPointDto>>of(List.of(), meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] shortSelling series load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return Mono.just(CardResult.<List<Feature2ShortSellingSeriesPointDto>>of(List.of(), meta));
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
                .flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        return Mono.just(CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta));
                    }

                    Feature2StockContext stockContext = stockContextOpt.get();
                    return feature2IndustryReader.resolve(stockContext.stock(), meta)
                            .flatMap(industryContextOpt -> {
                                if (industryContextOpt.isEmpty()) {
                                    return Mono.just(CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta));
                                }

                                Long industryId = industryContextOpt.get().industry().getIndustryId();
                                return Mono.fromCallable(() -> buildCheapRelatedStocks(
                                                stockContext.stock(),
                                                industryId,
                                                safeLimit
                                        ))
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .map(data -> CardResult.of(data, meta));
                            });
                })
                .switchIfEmpty(Mono.fromSupplier(() -> CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta)))
                .onErrorResume(ex -> {
                    log.warn("[Feature2CardService] relatedStocks card load failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    return Mono.just(CardResult.<List<Feature2RelatedStockCardDto>>of(List.of(), meta));
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
