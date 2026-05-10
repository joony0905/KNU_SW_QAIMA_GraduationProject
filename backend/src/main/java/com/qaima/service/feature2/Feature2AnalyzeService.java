package com.qaima.service.feature2;

import com.qaima.common.ErrorCode;
import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.BaseRate;
import com.qaima.domain.ShortSelling;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2ExplainMetricsDto;
import com.qaima.dto.feature2.Feature2ExplainRequestDto;
import com.qaima.dto.feature2.Feature2ExplainResponseDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.external.AnalysisApiClient;
import com.qaima.repository.BaseRateRepository;
import com.qaima.repository.ShortSellingRepository;
import com.qaima.service.baserate.BaseRateSyncService;
import com.qaima.service.feature2.model.Feature2Command;
import com.qaima.service.feature2.model.Feature2IndustryContext;
import com.qaima.service.feature2.model.Feature2StockContext;
import com.qaima.service.feature2.resolver.Feature2IndustryReader;
import com.qaima.service.feature2.resolver.Feature2StockResolver;
import com.qaima.service.feature2.support.Feature2MetricsAssembler;
import com.qaima.service.feature2.support.Feature2ExplainMetricsAssembler;
import com.qaima.service.feature2.support.Feature2RequestNormalizer;
import com.qaima.service.feature2.support.Feature2ResponseFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature2AnalyzeService {

    private final Feature2RequestNormalizer requestNormalizer;
    private final Feature2StockResolver stockResolver;
    private final Feature2IndustryReader industryReader;
    private final Feature2MetricsAssembler metricsAssembler;
    private final Feature2ExplainMetricsAssembler explainMetricsAssembler;
    private final Feature2ResponseFactory responseFactory;

    private final IndustryIndexService industryIndexService;
    private final ShortSellingFeatureService shortSellingFeatureService;
    private final PeerClusterService peerClusterService;
    private final BaseRateFeatureService baseRateFeatureService;
    private final NewsSentimentService newsSentimentService;
    private final Feature2CardService feature2CardService;
    private final AnalysisApiClient analysisApiClient;
    private final BaseRateRepository baseRateRepository;
    private final ShortSellingRepository shortSellingRepository;

    public Mono<Feature2AnalyzeResponseDto> analyze(Feature2AnalyzeRequestDto req) {
        final Feature2MetaDto meta = Feature2MetaDto.empty();
        final Feature2MetricsDto metrics = metricsAssembler.empty();
        final Feature2Command command = requestNormalizer.normalize(req);

        log.info("[Feature2][service-start] incoming req={}, normalized stockCode={}, freq={}, window={}, peerCount={}, maxLag={}, displayLimit={}, llmVendor={}",
                req, command.stockCode(), command.freq(), command.window(), command.peerCount(), command.maxLag(), command.displayLimit(), command.llmVendor());
        log.info("[Feat2] analyze start. stockCode={}, freq={}, window={}, peerCount={}, maxLag={}, displayLimit={}, llmVendor={}",
                command.stockCode(), command.freq(), command.window(), command.peerCount(), command.maxLag(), command.displayLimit(), command.llmVendor());

        if (command.stockCode() == null || command.stockCode().isBlank()) {
            meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
            log.warn("[Feature2][stock-resolve] STOCK_NOT_FOUND added: empty normalized stockCode. incomingReq={}", req);
            return Mono.fromSupplier(() -> responseFactory.success(metrics, meta));
        }

        return stockResolver.resolve(command.stockCode(), meta)
                .flatMap(stockContextOpt -> {
                    if (stockContextOpt.isEmpty()) {
                        log.warn("[Feature2][stock-resolve] STOCK_NOT_FOUND path reached. stockCode={}", command.stockCode());
                        return Mono.fromSupplier(() -> responseFactory.success(metrics, meta));
                    }

                    Feature2StockContext stockContext = stockContextOpt.get();
                    metricsAssembler.attachStock(metrics, stockContext);

                    return attachBaseRate(meta, metrics)
                            .then(attachMacroRates(command, meta, metrics))
                            .then(attachShortSelling(stockContext, meta, metrics))
                            .then(attachInvestorFlow(stockContext, command, meta, metrics))
                            .then(industryReader.resolve(stockContext.stock(), meta))
                            .flatMap(industryContextOpt -> {
                                if (industryContextOpt.isEmpty()) {
                                    log.info("[Feat2] early return: industry not resolved. stockCode={}",
                                            stockContext.stock().getStockCode());
                                    return Mono.fromSupplier(() -> responseFactory.success(metrics, meta));
                                }

                                Feature2IndustryContext industryContext = industryContextOpt.get();
                                metricsAssembler.attachIndustry(metrics, industryContext);

                                return attachIndustryIndex(industryContext, command, meta, metrics)
                                        .then(attachPeerCluster(stockContext, industryContext, command, meta, metrics))
                                        .then(attachTrendSummaries(stockContext, command, meta, metrics))
                                        .then(attachNews(stockContext, meta, metrics))
                                        .then(Mono.defer(() -> loadExplain(command, meta, metrics)))
                                        .defaultIfEmpty("")
                                        .map(explain -> responseFactory.success(
                                                metrics,
                                                meta,
                                                explain.isBlank() ? null : explain
                                        ));
                            });
                })
                .onErrorResume(ex -> {
                    log.warn("[Feature2AnalyzeService] analyze top-level failure. stockCode={}, cause={}",
                            command.stockCode(), ex.getMessage(), ex);
                    meta.addWarning(String.valueOf(ErrorCode.INTERNAL_ERROR));
                    return Mono.fromSupplier(() -> responseFactory.success(metrics, meta));
                });
    }

    private Mono<Void> attachBaseRate(
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        return baseRateFeatureService.loadLatest(meta)
                .doOnNext(baseRate -> metricsAssembler.attachBaseRate(metrics, baseRate))
                .then();
    }

    private Mono<Void> attachShortSelling(
            Feature2StockContext stockContext,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        return shortSellingFeatureService.loadLatest(stockContext.stock(), meta)
                .doOnNext(shortSelling -> metricsAssembler.attachShortSelling(metrics, shortSelling))
                .then();
    }

    private Mono<Void> attachMacroRates(
            Feature2Command command,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        int safeLimit = Math.max(30, Math.min(command.window(), 365));
        return Mono.zip(
                        feature2CardService.loadMacroRates(),
                        feature2CardService.loadMacroRatesSeries(safeLimit)
                )
                .doOnNext(tuple -> {
                    metricsAssembler.attachMacroRates(metrics, tuple.getT1().data());
                    metricsAssembler.attachMacroRatesSeries(metrics, tuple.getT2().data());
                    Optional.ofNullable(tuple.getT1().meta().getWarnings()).orElseGet(List::of).forEach(meta::addWarning);
                    Optional.ofNullable(tuple.getT2().meta().getWarnings()).orElseGet(List::of).forEach(meta::addWarning);
                })
                .onErrorResume(ex -> {
                    log.warn("[Feat2] macro rates load failed. stockCode={}, cause={}",
                            command.stockCode(), ex.getMessage(), ex);
                    meta.addWarning("MACRO_RATES_LOAD_FAILED");
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> attachInvestorFlow(
            Feature2StockContext stockContext,
            Feature2Command command,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        int safeLimit = Math.max(1, Math.min(command.window(), 252));
        return feature2CardService.loadInvestorFlow(stockContext.stock().getStockCode(), safeLimit)
                .doOnNext(result -> {
                    metricsAssembler.attachInvestorFlow(metrics, result.data());
                    Optional.ofNullable(result.meta().getWarnings()).orElseGet(List::of).forEach(meta::addWarning);
                })
                .onErrorResume(ex -> {
                    log.warn("[Feat2] investor flow load failed. stockCode={}, cause={}",
                            stockContext.stock().getStockCode(), ex.getMessage(), ex);
                    meta.addWarning("INVESTOR_FLOW_LOAD_FAILED");
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> attachIndustryIndex(
            Feature2IndustryContext industryContext,
            Feature2Command command,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        return industryIndexService.loadIndustryIndex(
                        industryContext.industry().getIndustryId(),
                        meta,
                        command.freq(),
                        command.window()
                )
                .doOnNext(industryIndex -> metricsAssembler.attachIndustryIndex(metrics, industryIndex))
                .then();
    }

    private Mono<Void> attachPeerCluster(
            Feature2StockContext stockContext,
            Feature2IndustryContext industryContext,
            Feature2Command command,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        return peerClusterService.getPeerCluster(
                        industryContext.industry().getIndustryId(),
                        stockContext.stock().getStockCode(),
                        command.freq(),
                        command.window(),
                        command.from(),
                        command.to(),
                        command.peerCount(),
                        command.maxLag(),
                        command.displayLimit()
                )
                .onErrorResume(ex -> {
                    log.warn("[Feat2] peerCluster load failed. industryId={}, stockCode={}, cause={}",
                            industryContext.industry().getIndustryId(),
                            stockContext.stock().getStockCode(),
                            ex.getMessage(),
                            ex);
                    meta.addWarning(Feat2WarningCode.PEER_CLUSTER_MISSING);
                    return Mono.just(PeerClusterResult.builder().peerCluster(null).build());
                })
                .doOnNext(result -> {
                    metricsAssembler.attachPeerCluster(metrics, result.getPeerCluster());
                    Optional.ofNullable(result.getWarnings())
                            .orElseGet(List::of)
                            .forEach(meta::addWarning);
                })
                .then();
    }

    private Mono<Void> attachNews(
            Feature2StockContext stockContext,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        return newsSentimentService.loadNews(stockContext.stock())
                .onErrorResume(ex -> {
                    log.warn("[Feat2] news load failed. stockCode={}, cause={}",
                            stockContext.stock().getStockCode(),
                            ex.getMessage(),
                            ex);
                    return Mono.just(NewsLoadResult.builder()
                            .newsList(List.of())
                            .warnings(List.of("NEWS_LIST_FETCH_FAILED"))
                            .build());
                })
                .doOnNext(result -> {
                    metricsAssembler.attachNewsList(metrics, result.getNewsList());
                    metricsAssembler.attachNewsSentimentSummary(metrics, buildNewsSentimentSummary(result.getNewsList()));
                    Optional.ofNullable(result.getWarnings())
                            .orElseGet(List::of)
                            .forEach(meta::addWarning);
                })
                .then();
    }

    private Mono<Void> attachTrendSummaries(
            Feature2StockContext stockContext,
            Feature2Command command,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        int safeWindow = Math.max(1, Math.min(command.window(), 365));
        return Mono.when(
                attachBaseRateTrendSummary(safeWindow, meta, metrics),
                attachShortSellingTrendSummary(stockContext, safeWindow, meta, metrics)
        ).then();
    }

    private Mono<Void> attachBaseRateTrendSummary(
            int window,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        return Mono.fromCallable(() -> baseRateRepository.findByStatCodeAndItemCodeAndCycleOrderByBaseDateDesc(
                        BaseRateSyncService.DEFAULT_STAT_CODE,
                        BaseRateSyncService.DEFAULT_ITEM_CODE,
                        BaseRateSyncService.DEFAULT_CYCLE,
                        PageRequest.of(0, Math.max(1, Math.min(window, 1095)))
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .map(rows -> rows.stream()
                        .sorted(Comparator.comparing(entity -> entity.getBaseDate()))
                        .toList())
                .doOnNext(rows -> metricsAssembler.attachBaseRateTrendSummary(
                        metrics,
                        buildBaseRateTrendSummary(window, rows)
                ))
                .onErrorResume(ex -> {
                    log.warn("[Feat2] baseRate trend summary failed. cause={}", ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.BASE_RATE_LOAD_FAILED);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> attachShortSellingTrendSummary(
            Feature2StockContext stockContext,
            int window,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        if (stockContext == null || stockContext.stock() == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> shortSellingRepository.findByStockOrderByReportDateDesc(
                        stockContext.stock(),
                        PageRequest.of(0, Math.max(1, Math.min(window, 252)))
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .map(rows -> rows.stream()
                        .sorted(Comparator.comparing(entity -> entity.getReportDate()))
                        .toList())
                .doOnNext(rows -> metricsAssembler.attachShortSellingTrendSummary(
                        metrics,
                        buildShortSellingTrendSummary(window, rows)
                ))
                .onErrorResume(ex -> {
                    log.warn("[Feat2] shortSelling trend summary failed. stockCode={}, cause={}",
                            stockContext.stock().getStockCode(), ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.SHORT_SELLING_LOAD_FAILED);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<String> loadExplain(
            Feature2Command command,
            Feature2MetaDto meta,
            Feature2MetricsDto metrics
    ) {
        Feature2ExplainMetricsDto explainMetrics = explainMetricsAssembler.from(metrics);
        log.info(
                "[Feat2][LLM][summary-input] stockCode={}, fullIndustryIndex={}, fullPeerCluster={}, fullNewsCount={}, summaryIndustryIndex={}, summaryPeerCluster={}, summaryTopPeers={}, summaryRecentNews={}",
                command.stockCode(),
                metrics.getIndustryIndex() != null,
                metrics.getPeerCluster() != null,
                metrics.getNewsList() == null ? 0 : metrics.getNewsList().size(),
                explainMetrics.getIndustryIndex() != null,
                explainMetrics.getPeerClusterSummary() != null,
                explainMetrics.getPeerClusterSummary() == null || explainMetrics.getPeerClusterSummary().getTopPeers() == null
                        ? 0
                        : explainMetrics.getPeerClusterSummary().getTopPeers().size(),
                explainMetrics.getRecentNews() == null ? 0 : explainMetrics.getRecentNews().size()
        );

        Feature2ExplainRequestDto request = Feature2ExplainRequestDto.builder()
                .stockCode(command.stockCode())
                .freq(command.freq())
                .window(command.window())
                .llmVendor(command.llmVendor())
                .metrics(explainMetrics)
                .build();

        return analysisApiClient.requestFeature2Explain(request)
                .doOnNext(response -> Optional.ofNullable(response.getWarnings())
                        .orElseGet(List::of)
                        .forEach(meta::addWarning))
                .flatMap(response -> {
                    String explain = response.getExplain();
                    if (explain == null || explain.isBlank()) {
                        return Mono.empty();
                    }
                    return Mono.just(explain);
                })
                .onErrorResume(ex -> {
                    log.warn("[Feat2] LLM explain failed. stockCode={}, llmVendor={}, cause={}",
                            command.stockCode(), command.llmVendor(), ex.getMessage(), ex);
                    meta.addWarning("LLM_EXPLAIN_FAILED");
                    return Mono.empty();
                });
    }

    private Feature2MetricsDto.NewsSentimentSummary buildNewsSentimentSummary(List<NewsItemDto> newsList) {
        if (newsList == null || newsList.isEmpty()) {
            return null;
        }

        List<NewsItemDto> scoredNews = newsList.stream()
                .filter(item -> item != null && item.getSentimentScore() != null)
                .sorted(Comparator.comparing(NewsItemDto::getPublishedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (scoredNews.isEmpty()) {
            return null;
        }

        LocalDate summaryDate = scoredNews.stream()
                .map(NewsItemDto::getPublishedAt)
                .filter(java.util.Objects::nonNull)
                .map(publishedAt -> publishedAt.toLocalDate())
                .findFirst()
                .orElse(null);
        List<NewsItemDto> dailyNews = summaryDate == null
                ? scoredNews
                : scoredNews.stream()
                .filter(item -> item.getPublishedAt() != null
                        && summaryDate.equals(item.getPublishedAt().toLocalDate()))
                .toList();

        List<NewsItemDto> avgTargets = dailyNews.isEmpty() ? scoredNews : dailyNews;
        BigDecimal dailyAvgScore = avgTargets.stream()
                .map(NewsItemDto::getSentimentScore)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(avgTargets.size()), 6, RoundingMode.HALF_UP);

        List<Feature2MetricsDto.RecentNewsSentiment> recentItems = scoredNews.stream()
                .limit(5)
                .map(item -> Feature2MetricsDto.RecentNewsSentiment.builder()
                        .newsId(item.getNewsId())
                        .title(item.getTitle())
                        .publisher(item.getPublisher())
                        .publishedAt(item.getPublishedAt())
                        .sentimentScore(item.getSentimentScore())
                        .sentimentLabel(toSentimentLabel(item.getSentimentScore()))
                        .build())
                .toList();

        return Feature2MetricsDto.NewsSentimentSummary.builder()
                .summaryDate(summaryDate)
                .dailyAvgScore(dailyAvgScore)
                .dailyNewsCount(avgTargets.size())
                .scoredNewsCount(scoredNews.size())
                .positiveCount(countByLabel(scoredNews, "positive"))
                .neutralCount(countByLabel(scoredNews, "neutral"))
                .negativeCount(countByLabel(scoredNews, "negative"))
                .strongestPositiveScore(scoredNews.stream()
                        .map(NewsItemDto::getSentimentScore)
                        .max(BigDecimal::compareTo)
                        .orElse(null))
                .strongestNegativeScore(scoredNews.stream()
                        .map(NewsItemDto::getSentimentScore)
                        .min(BigDecimal::compareTo)
                        .orElse(null))
                .recentItems(recentItems)
                .build();
    }

    private Feature2MetricsDto.BaseRateTrendSummary buildBaseRateTrendSummary(
            int window,
            List<BaseRate> rows
    ) {
        List<BaseRate> validRows = rows == null
                ? List.of()
                : rows.stream()
                .filter(row -> row != null && row.getBaseDate() != null && row.getRateValue() != null)
                .toList();
        if (validRows.isEmpty()) {
            return null;
        }

        BaseRate first = validRows.get(0);
        BaseRate last = validRows.get(validRows.size() - 1);
        BigDecimal change = subtract(last.getRateValue(), first.getRateValue());
        return Feature2MetricsDto.BaseRateTrendSummary.builder()
                .window(window)
                .pointCount(validRows.size())
                .startDate(first.getBaseDate())
                .endDate(last.getBaseDate())
                .startValue(first.getRateValue())
                .endValue(last.getRateValue())
                .change(change)
                .direction(toDirection(change))
                .unit(last.getUnitName())
                .build();
    }

    private Feature2MetricsDto.ShortSellingTrendSummary buildShortSellingTrendSummary(
            int window,
            List<ShortSelling> rows
    ) {
        List<ShortSelling> validRows = rows == null
                ? List.of()
                : rows.stream()
                .filter(row -> row != null && row.getReportDate() != null)
                .toList();
        if (validRows.isEmpty()) {
            return null;
        }

        ShortSelling first = validRows.get(0);
        ShortSelling last = validRows.get(validRows.size() - 1);
        BigDecimal shortVolumeRatioChange = subtract(last.getShortVolumeRatio(), first.getShortVolumeRatio());
        BigDecimal shortAmountRatioChange = subtract(last.getShortAmountRatio(), first.getShortAmountRatio());
        BigDecimal directionBasis = shortAmountRatioChange != null ? shortAmountRatioChange : shortVolumeRatioChange;

        return Feature2MetricsDto.ShortSellingTrendSummary.builder()
                .window(window)
                .pointCount(validRows.size())
                .startDate(first.getReportDate())
                .endDate(last.getReportDate())
                .startShortVolumeRatio(first.getShortVolumeRatio())
                .endShortVolumeRatio(last.getShortVolumeRatio())
                .shortVolumeRatioChange(shortVolumeRatioChange)
                .avgShortVolumeRatio(average(validRows.stream()
                        .map(ShortSelling::getShortVolumeRatio)
                        .filter(Objects::nonNull)
                        .toList()))
                .maxShortVolumeRatio(validRows.stream()
                        .map(ShortSelling::getShortVolumeRatio)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null))
                .startShortAmountRatio(first.getShortAmountRatio())
                .endShortAmountRatio(last.getShortAmountRatio())
                .shortAmountRatioChange(shortAmountRatioChange)
                .avgShortAmountRatio(average(validRows.stream()
                        .map(ShortSelling::getShortAmountRatio)
                        .filter(Objects::nonNull)
                        .toList()))
                .maxShortAmountRatio(validRows.stream()
                        .map(ShortSelling::getShortAmountRatio)
                        .filter(Objects::nonNull)
                        .max(BigDecimal::compareTo)
                        .orElse(null))
                .direction(toDirection(directionBasis))
                .build();
    }

    private BigDecimal subtract(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null) {
            return null;
        }
        return current.subtract(previous);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private String toDirection(BigDecimal change) {
        if (change == null) {
            return "UNKNOWN";
        }
        int comparison = change.compareTo(BigDecimal.ZERO);
        if (comparison > 0) {
            return "UP";
        }
        if (comparison < 0) {
            return "DOWN";
        }
        return "FLAT";
    }

    private int countByLabel(List<NewsItemDto> newsList, String label) {
        if (newsList == null || label == null) {
            return 0;
        }
        return (int) newsList.stream()
                .filter(item -> label.equals(toSentimentLabel(item.getSentimentScore())))
                .count();
    }

    private String toSentimentLabel(BigDecimal sentimentScore) {
        if (sentimentScore == null) {
            return "neutral";
        }
        if (sentimentScore.compareTo(BigDecimal.valueOf(0.05)) > 0) {
            return "positive";
        }
        if (sentimentScore.compareTo(BigDecimal.valueOf(-0.05)) < 0) {
            return "negative";
        }
        return "neutral";
    }
}
