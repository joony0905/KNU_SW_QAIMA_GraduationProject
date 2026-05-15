package com.qaima.service.feature3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.domain.Freq;
import com.qaima.dto.featone.FeatOneAnalysisMetricsDto;
import com.qaima.dto.featone.FeatOneGrowthDto;
import com.qaima.dto.featone.FeatOneMarketSnapshotDto;
import com.qaima.dto.featone.FeatOneProfitabilityDto;
import com.qaima.dto.featone.FeatOneStabilityDto;
import com.qaima.dto.featone.FeatOneValuationDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature3.Feature3OverlayCachePreviewRequestDto;
import com.qaima.dto.feature3.Feature3OverlayCachePreviewResponseDto;
import com.qaima.dto.feature3.PortfolioAnalyzeRequestDto;
import com.qaima.dto.feature3.PortfolioAnalyzeResponseDto;
import com.qaima.dto.indicator.IndicatorBundleDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.news.NewsItemDto;
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.dto.peercluster.PeerItemDto;
import com.qaima.external.dto.feature3.Feature3FastApiAnalyzeRequestDto;
import com.qaima.service.featone.FeatOneService;
import com.qaima.service.feature2.IndustryIndexService;
import com.qaima.service.feature2.NewsLoadResult;
import com.qaima.service.feature2.NewsSentimentService;
import com.qaima.service.feature2.PeerClusterResult;
import com.qaima.service.feature2.PeerClusterService;
import com.qaima.service.feature2.model.Feature2IndustryContext;
import com.qaima.service.feature2.model.Feature2StockContext;
import com.qaima.service.feature2.resolver.Feature2IndustryReader;
import com.qaima.service.feature2.resolver.Feature2StockResolver;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature3OverlayService {

    private static final Duration FRESH_TTL = Duration.ofHours(24);
    private static final Duration STALE_TTL = Duration.ofDays(7);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final FeatOneService featOneService;
    private final Feature2StockResolver stockResolver;
    private final Feature2IndustryReader industryReader;
    private final IndustryIndexService industryIndexService;
    private final PeerClusterService peerClusterService;
    private final NewsSentimentService newsSentimentService;

    public Mono<Feature3OverlayCachePreviewResponseDto> preview(Feature3OverlayCachePreviewRequestDto req) {
        List<String> overlays = req.selectedOverlays() != null ? req.selectedOverlays() : List.of();
        List<String> stockCodes = req.stockCodes() != null ? req.stockCodes() : List.of();
        if (overlays.isEmpty()) {
            return Mono.just(new Feature3OverlayCachePreviewResponseDto(
                    1, 0, 1, List.of(), "Core 분석만 실행하면 1 credit이 차감됩니다."
            ));
        }

        return Flux.fromIterable(overlays)
                .flatMap(overlay -> cacheStatusForOverlay(overlay, stockCodes)
                        .map(status -> toPreviewItem(overlay, status)))
                .collectList()
                .map(items -> {
                    int overlayCredit = items.stream()
                            .mapToInt(item -> item.additionalCredit() != null ? item.additionalCredit() : 0)
                            .sum();
                    return new Feature3OverlayCachePreviewResponseDto(
                            1,
                            overlayCredit,
                            1 + overlayCredit,
                            items,
                            "Core 분석은 1 credit이며, 캐시가 없는 체크 항목마다 1 credit이 추가됩니다."
                    );
                });
    }

    public Mono<Long> estimateCredit(PortfolioAnalyzeRequestDto req) {
        List<String> overlays = req.options() != null && req.options().selectedOverlays() != null
                ? req.options().selectedOverlays()
                : List.of();
        List<String> stockCodes = req.holdings() != null
                ? req.holdings().stream().map(PortfolioAnalyzeRequestDto.Holding::stockCode).toList()
                : List.of();
        if (overlays.isEmpty()) {
            return Mono.just(1L);
        }
        // 현재 구현: Core 1 credit + Redis HIT가 아닌 overlay 묶음당 1 credit을 실제 차감 기준으로 사용한다.
        // 진행 예정: FORCE_REFRESH/REFRESH_MISSING_ONLY 정책별 차감 차이를 더 세분화한다.
        return Flux.fromIterable(overlays)
                .flatMap(overlay -> cacheStatusForOverlay(overlay, stockCodes))
                .map(status -> "HIT".equals(status) ? 0L : 1L)
                .reduce(1L, Long::sum);
    }

    public Mono<PortfolioAnalyzeResponseDto> enrich(
            PortfolioAnalyzeRequestDto req,
            PortfolioAnalyzeResponseDto response
    ) {
        List<String> selected = req.options() != null && req.options().selectedOverlays() != null
                ? req.options().selectedOverlays()
                : List.of();
        if (selected.isEmpty()) {
            return Mono.just(withOverlayAndExplain(response, emptyOverlay(), explainOrDeterministic(response)));
        }
        PortfolioAnalyzeResponseDto.OverlayResult overlayResult = new PortfolioAnalyzeResponseDto.OverlayResult(
                List.of(),
                List.of(),
                List.of(),
                response.overlays() != null ? response.overlays().overlaySignals() : List.of(),
                response.overlays() != null ? response.overlays().adjustedPortfolios() : List.of(),
                response.overlays() != null ? response.overlays().visualizations() : List.of(),
                response.overlays() != null ? response.overlays().explanations() : List.of()
        );
        return Mono.just(withOverlayAndExplain(response, overlayResult, explainOrDeterministic(response)));
    }

    public Mono<List<Feature3FastApiAnalyzeRequestDto.OverlaySignal>> loadOverlaySignals(PortfolioAnalyzeRequestDto req) {
        List<String> selected = req.options() != null && req.options().selectedOverlays() != null
                ? req.options().selectedOverlays()
                : List.of();
        if (selected.isEmpty()) {
            return Mono.just(List.of());
        }
        List<PortfolioAnalyzeRequestDto.Holding> holdings = req.holdings() != null ? req.holdings() : List.of();
        List<PortfolioAnalyzeRequestDto.Holding> targetHoldings = holdings.stream().limit(3).toList();
        return Flux.fromIterable(selected)
                .flatMap(overlay -> loadOverlayForSignals(overlay, targetHoldings)
                        .onErrorResume(ex -> {
                            log.warn("[Feature3Overlay] overlay signal load failed. overlay={}, cause={}",
                                    overlay, ex.getMessage(), ex);
                            return Mono.just(fallbackBundle(overlay, "MISS"));
                        }))
                .collectList()
                .map(bundles -> bundles.stream()
                        .flatMap(bundle -> toOverlaySignals(bundle).stream())
                        .toList());
    }

    private Mono<OverlayBundle> loadOverlayForSignals(
            String overlay,
            List<PortfolioAnalyzeRequestDto.Holding> holdings
    ) {
        if (holdings.isEmpty()) {
            return Mono.just(fallbackBundle(overlay, "MISS"));
        }
        return Flux.fromIterable(holdings)
                .flatMap(holding -> buildOverlayBundle(overlay, holding)
                        .flatMap(bundle -> writeCard(overlay, holding.stockCode(), bundle.cards().get(0))
                                .onErrorResume(ex -> {
                                    log.warn("[Feature3Overlay] overlay cache write skipped. overlay={}, stockCode={}, cause={}",
                                            overlay, holding.stockCode(), ex.getMessage());
                                    return Mono.just(false);
                                })
                                .thenReturn(bundle)))
                .collectList()
                .map(this::mergeBundles);
    }

    private Mono<String> cacheStatusForOverlay(String overlay, List<String> stockCodes) {
        if (stockCodes.isEmpty()) {
            return Mono.just("MISS");
        }
        return Flux.fromIterable(stockCodes)
                .flatMap(stockCode -> redisTemplate.hasKey(primaryFreshKey(overlay, stockCode))
                        .flatMap(fresh -> {
                            if (Boolean.TRUE.equals(fresh)) {
                                return Mono.just("HIT");
                            }
                            return redisTemplate.hasKey(primaryStaleKey(overlay, stockCode))
                                    .map(stale -> Boolean.TRUE.equals(stale) ? "STALE" : "MISS");
                        })
                        .onErrorReturn("MISS"))
                .collectList()
                .map(statuses -> {
                    if (statuses.stream().allMatch("HIT"::equals)) {
                        return "HIT";
                    }
                    if (statuses.stream().anyMatch(status -> status.equals("HIT") || status.equals("STALE"))) {
                        return "STALE";
                    }
                    return "MISS";
                });
    }

    private Feature3OverlayCachePreviewResponseDto.OverlayCacheItem toPreviewItem(String overlay, String status) {
        boolean needsRefresh = !"HIT".equals(status);
        return new Feature3OverlayCachePreviewResponseDto.OverlayCacheItem(
                overlay,
                status,
                needsRefresh ? 1 : 0,
                needsRefresh,
                switch (status) {
                    case "HIT" -> "최근 분석 캐시를 재사용할 수 있어 추가 credit이 필요하지 않습니다.";
                    case "STALE" -> "이전 분석 캐시가 있으나 기준 시점이 오래되어 새로 분석할지 확인이 필요합니다.";
                    default -> "캐시된 분석 결과가 없어 새 분석 시 1 credit이 추가됩니다.";
                }
        );
    }

    private Mono<OverlayBundle> loadOverlay(
            String overlay,
            List<PortfolioAnalyzeRequestDto.Holding> holdings
    ) {
        if (holdings.isEmpty()) {
            return Mono.just(fallbackBundle(overlay, "MISS"));
        }
        return Flux.fromIterable(holdings)
                .flatMap(holding -> readCard(overlay, holding.stockCode())
                        .map(card -> bundleFromCard(card, holding, card.description()))
                        .switchIfEmpty(Mono.defer(() -> buildOverlayBundle(overlay, holding)))
                        .flatMap(bundle -> writeCard(overlay, holding.stockCode(), bundle.cards().get(0))
                                .onErrorResume(ex -> {
                                    log.warn("[Feature3Overlay] overlay cache write skipped. overlay={}, stockCode={}, cause={}",
                                            overlay, holding.stockCode(), ex.getMessage());
                                    return Mono.just(false);
                                })
                                .thenReturn(bundle)))
                .collectList()
                .map(this::mergeBundles);
    }

    private Mono<OverlayBundle> buildOverlayBundle(
            String overlay,
            PortfolioAnalyzeRequestDto.Holding holding
    ) {
        if (usesFeature1(overlay)) {
            return loadFeature1Metrics(holding.stockCode())
                    .map(cached -> "technical".equals(overlay)
                            ? technicalBundle(holding, cached.metrics(), cached.cacheStatus())
                            : feature1Bundle(holding, cached.metrics(), cached.cacheStatus()))
                    .onErrorResume(ex -> {
                        log.warn("[Feature3Overlay] feature1 overlay failed. overlay={}, stockCode={}, cause={}",
                                overlay, holding.stockCode(), ex.getMessage(), ex);
                        return Mono.just(fallbackBundle(overlay, "MISS"));
                    });
        }

        return switch (overlay) {
            case "industry" -> loadIndustryBundle(holding).onErrorResume(ex -> {
                log.warn("[Feature3Overlay] industry overlay failed. stockCode={}, cause={}",
                        holding.stockCode(), ex.getMessage(), ex);
                return Mono.just(fallbackBundle(overlay, "MISS"));
            });
            case "correlation" -> loadPeerClusterBundle(holding).onErrorResume(ex -> {
                log.warn("[Feature3Overlay] correlation overlay failed. stockCode={}, cause={}",
                        holding.stockCode(), ex.getMessage(), ex);
                return Mono.just(fallbackBundle(overlay, "MISS"));
            });
            case "news" -> loadNewsBundle(holding).onErrorResume(ex -> {
                log.warn("[Feature3Overlay] news overlay failed. stockCode={}, cause={}",
                        holding.stockCode(), ex.getMessage(), ex);
                return Mono.just(fallbackBundle(overlay, "MISS"));
            });
            default -> Mono.just(fallbackBundle(overlay, "MISS"));
        };
    }

    private Mono<CachedFeature1Metrics> loadFeature1Metrics(String stockCode) {
        LocalDate to = LocalDate.now(KST);
        LocalDate from = to.minusDays(370);
        return readFeature1Metrics(stockCode)
                .map(metrics -> new CachedFeature1Metrics(metrics, "HIT"))
                .switchIfEmpty(Mono.defer(() -> featOneService.getFeatOneData(
                                stockCode,
                                Freq.ONE_D,
                                from.toString(),
                                to.toString(),
                                "",
                                false,
                                null
                        )
                        .flatMap(result -> writeFeature1Metrics(
                                        stockCode,
                                        result.getData() != null ? result.getData().getMetrics() : null
                                )
                                .thenReturn(new CachedFeature1Metrics(
                                        result.getData() != null ? result.getData().getMetrics() : null,
                                        "MISS"
                                )))));
    }

    public Mono<Boolean> cacheFeature1Metrics(String stockCode, FeatOneAnalysisMetricsDto metrics) {
        return writeFeature1Metrics(stockCode, metrics);
    }

    private OverlayBundle feature1Bundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            FeatOneAnalysisMetricsDto metrics,
            String cacheStatus
    ) {
        boolean hasMetrics = metrics != null;
        boolean hasSnapshot = hasMetrics && metrics.getMarketSnapshot() != null;
        String value = hasSnapshot
                ? fundamentalsValue(metrics.getMarketSnapshot())
                : hasMetrics && metrics.getOhlcvSummary() != null
                        ? "가격 관측치 " + safe(metrics.getOhlcvSummary().getCount()) + "개"
                        : null;
        String description = hasSnapshot
                ? "Feature1 재무/밸류에이션 지표를 core risk와 분리된 종목 품질 참고 정보로 표시합니다."
                : "Feature1 종목 건강도 데이터가 부족해 보조 해석을 제한적으로 표시합니다.";
        PortfolioAnalyzeResponseDto.OverlayInsightCard card = new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                "fundamentals",
                overlayTitle("fundamentals", holding),
                description,
                hasSnapshot ? "INFO" : "WARN",
                "FEATURE1",
                cacheStatus,
                List.of(holding.stockCode())
        );
        PortfolioAnalyzeResponseDto.HoldingOverlayRow row = holdingRow(holding, "fundamentals", "종목 건강도", value, hasSnapshot ? "INFO" : "WARN", "FEATURE1", cacheStatus);
        return new OverlayBundle(List.of(card), List.of(row), List.of(exposure(card, row)));
    }

    private String fundamentalsValue(FeatOneMarketSnapshotDto snapshot) {
        if (snapshot == null) {
            return null;
        }
        FeatOneValuationDto valuation = snapshot.getValuation();
        FeatOneProfitabilityDto profitability = snapshot.getProfitability();
        FeatOneStabilityDto stability = snapshot.getStability();
        FeatOneGrowthDto growth = snapshot.getGrowth();
        List<String> parts = new ArrayList<>();
        if (valuation != null) {
            if (valuation.getPer() != null) parts.add("PER=" + safe(valuation.getPer()));
            if (valuation.getPbr() != null) parts.add("PBR=" + safe(valuation.getPbr()));
            if (valuation.getPsr() != null) parts.add("PSR=" + safe(valuation.getPsr()));
        }
        if (profitability != null) {
            if (profitability.getRoe() != null) parts.add("ROE=" + safe(profitability.getRoe()));
            if (profitability.getOperatingMargin() != null) parts.add("OPM=" + safe(profitability.getOperatingMargin()));
            if (profitability.getNetMargin() != null) parts.add("NPM=" + safe(profitability.getNetMargin()));
        }
        if (stability != null) {
            if (stability.getDebtRatio() != null) parts.add("Debt=" + safe(stability.getDebtRatio()));
            if (stability.getCurrentRatio() != null) parts.add("Current=" + safe(stability.getCurrentRatio()));
        }
        if (growth != null) {
            if (growth.getRevenueGrowth() != null) parts.add("RevenueGrowth=" + safe(growth.getRevenueGrowth()));
            if (growth.getEpsGrowth() != null) parts.add("EPSGrowth=" + safe(growth.getEpsGrowth()));
        }
        return parts.isEmpty() ? "재무/밸류에이션 데이터 부족" : String.join(", ", parts);
    }

    private OverlayBundle technicalBundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            FeatOneAnalysisMetricsDto metrics,
            String cacheStatus
    ) {
        IndicatorBundleDto indicators = metrics != null
                ? metrics.getIndicators()
                : null;
        String summary = metrics != null
                ? metrics.getIndicatorSummary()
                : null;
        boolean hasIndicators = indicators != null;
        String value = hasIndicators
                ? firstNonBlank(summary, indicatorAvailability(indicators))
                : null;
        String severity = hasIndicators ? "INFO" : "WARN";
        PortfolioAnalyzeResponseDto.OverlayInsightCard card = new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                "technical",
                overlayTitle("technical", holding),
                hasIndicators
                        ? "Feature1 indicator 데이터를 core risk와 분리된 기술적 지표 참고 정보로 표시합니다."
                        : "Feature1 indicator 데이터가 부족해 기술적 보조 해석을 제한적으로 표시합니다.",
                severity,
                "FEATURE1",
                cacheStatus,
                List.of(holding.stockCode())
        );
        PortfolioAnalyzeResponseDto.HoldingOverlayRow row = holdingRow(
                holding,
                "technical",
                "기술적 지표",
                value,
                severity,
                "FEATURE1",
                cacheStatus
        );
        return new OverlayBundle(List.of(card), List.of(row), List.of(exposure(card, row)));
    }

    private Mono<OverlayBundle> loadIndustryBundle(PortfolioAnalyzeRequestDto.Holding holding) {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        return resolveStockAndIndustry(holding.stockCode(), meta)
                .flatMap(context -> industryIndexService.loadIndustryIndex(context.industry().industry().getIndustryId(), meta, Freq.ONE_D, 252)
                        .map(index -> industryBundle(holding, context.industry(), index, "MISS"))
                        .switchIfEmpty(Mono.just(industryBundle(holding, context.industry(), null, "MISS"))))
                .switchIfEmpty(Mono.just(fallbackBundle("industry", "MISS")));
    }

    private Mono<OverlayBundle> loadPeerClusterBundle(PortfolioAnalyzeRequestDto.Holding holding) {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        return resolveStockAndIndustry(holding.stockCode(), meta)
                .flatMap(context -> peerClusterService.getPeerCluster(
                                context.industry().industry().getIndustryId(),
                                context.stock().stock().getStockCode(),
                                Freq.ONE_D,
                                252,
                                null,
                                null,
                                8,
                                5,
                                8
                        )
                        .map(result -> peerClusterBundle(holding, result, "MISS"))
                        .switchIfEmpty(Mono.just(fallbackBundle("correlation", "MISS"))))
                .switchIfEmpty(Mono.just(fallbackBundle("correlation", "MISS")));
    }

    private Mono<OverlayBundle> loadNewsBundle(PortfolioAnalyzeRequestDto.Holding holding) {
        Feature2MetaDto meta = Feature2MetaDto.empty();
        return resolveStock(holding.stockCode(), meta)
                .flatMap(context -> newsSentimentService.loadNews(context.stock())
                        .map(result -> newsBundle(holding, result, "MISS"))
                        .switchIfEmpty(Mono.just(fallbackBundle("news", "MISS"))))
                .switchIfEmpty(Mono.just(fallbackBundle("news", "MISS")));
    }

    private Mono<Feature2StockContext> resolveStock(String stockCode, Feature2MetaDto meta) {
        return stockResolver.resolve(stockCode, meta)
                .flatMap(optional -> optional.map(Mono::just).orElseGet(Mono::empty));
    }

    private Mono<StockIndustryContext> resolveStockAndIndustry(String stockCode, Feature2MetaDto meta) {
        return resolveStock(stockCode, meta)
                .flatMap(stock -> industryReader.resolve(stock.stock(), meta)
                        .flatMap(optional -> optional
                                .map(industry -> Mono.just(new StockIndustryContext(stock, industry)))
                                .orElseGet(Mono::empty)));
    }

    private OverlayBundle industryBundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            Feature2IndustryContext industry,
            IndustryIndexBlockDto index,
            String cacheStatus
    ) {
        String industryName = industry != null && industry.industryMeta() != null ? industry.industryMeta().getName() : null;
        String indexName = index != null ? index.getName() : null;
        String value = firstNonBlank(industryName, indexName);
        String description = value != null
                ? "Feature2 산업 reader와 업종 지수 데이터를 core risk와 분리된 산업 집중도 참고 정보로 표시합니다."
                : "산업 메타데이터가 부족합니다.";
        String severity = value != null ? "INFO" : "WARN";
        PortfolioAnalyzeResponseDto.OverlayInsightCard card = new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                "industry",
                overlayTitle("industry", holding),
                description,
                severity,
                "FEATURE2_INDUSTRY",
                cacheStatus,
                List.of(holding.stockCode())
        );
        PortfolioAnalyzeResponseDto.HoldingOverlayRow row = holdingRow(
                holding,
                "industry",
                "산업 집중도",
                value != null && indexName != null ? value + " / " + indexName : value,
                severity,
                "FEATURE2_INDUSTRY",
                cacheStatus
        );
        return new OverlayBundle(List.of(card), List.of(row), List.of(exposure(card, row)));
    }

    private OverlayBundle peerClusterBundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            PeerClusterResult result,
            String cacheStatus
    ) {
        PeerClusterDto peerCluster = result != null ? result.getPeerCluster() : null;
        OverlayMetric metric = peerClusterMetric(peerCluster);
        PortfolioAnalyzeResponseDto.OverlayInsightCard card = new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                "correlation",
                overlayTitle("correlation", holding),
                metric.description(),
                metric.severity(),
                "FEATURE2_PEERCLUSTER",
                cacheStatus,
                List.of(holding.stockCode())
        );
        PortfolioAnalyzeResponseDto.HoldingOverlayRow row = holdingRow(
                holding,
                "correlation",
                "Peer 동행 참고",
                metric.value(),
                metric.severity(),
                "FEATURE2_PEERCLUSTER",
                cacheStatus
        );
        return new OverlayBundle(List.of(card), List.of(row), List.of(exposure(card, row)));
    }

    private OverlayBundle newsBundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            NewsLoadResult result,
            String cacheStatus
    ) {
        List<NewsItemDto> newsList = result != null && result.getNewsList() != null ? result.getNewsList() : List.of();
        long sentimentCount = newsList.stream().filter(item -> item.getSentimentScore() != null).count();
        BigDecimal sentimentSum = newsList.stream()
                .map(NewsItemDto::getSentimentScore)
                .filter(score -> score != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sentimentCount > 0
                ? sentimentSum.divide(BigDecimal.valueOf(sentimentCount), 4, java.math.RoundingMode.HALF_UP)
                : null;
        String value = newsList.isEmpty()
                ? null
                : "score=" + safe(avg) + ", count=" + newsList.size();
        String severity = avg != null && avg.compareTo(BigDecimal.ZERO) < 0 ? "WARN" : newsList.isEmpty() ? "WARN" : "INFO";
        PortfolioAnalyzeResponseDto.OverlayInsightCard card = new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                "news",
                overlayTitle("news", holding),
                newsList.isEmpty()
                        ? "뉴스 데이터가 부족합니다."
                        : "Feature2 news sentiment 데이터를 core risk와 분리된 뉴스 흐름 참고 정보로 표시합니다.",
                severity,
                "FEATURE2_NEWS",
                cacheStatus,
                List.of(holding.stockCode())
        );
        PortfolioAnalyzeResponseDto.HoldingOverlayRow row = holdingRow(
                holding,
                "news",
                "뉴스 감성",
                value,
                severity,
                "FEATURE2_NEWS",
                cacheStatus
        );
        return new OverlayBundle(List.of(card), List.of(row), List.of(exposure(card, row)));
    }

    private Mono<PortfolioAnalyzeResponseDto.OverlayInsightCard> readCard(String overlay, String stockCode) {
        return redisTemplate.opsForValue()
                .get(freshKey(overlay, stockCode))
                .onErrorResume(ex -> {
                    log.warn("[Feature3Overlay] overlay cache read skipped. overlay={}, stockCode={}, cause={}",
                            overlay, stockCode, ex.getMessage());
                    return Mono.empty();
                })
                .flatMap(json -> readCachedCard(json, "HIT"))
                .switchIfEmpty(redisTemplate.opsForValue()
                        .get(staleKey(overlay, stockCode))
                        .onErrorResume(ex -> {
                            log.warn("[Feature3Overlay] overlay stale cache read skipped. overlay={}, stockCode={}, cause={}",
                                    overlay, stockCode, ex.getMessage());
                            return Mono.empty();
                        })
                        .flatMap(json -> readCachedCard(json, "STALE")));
    }

    private Mono<FeatOneAnalysisMetricsDto> readFeature1Metrics(String stockCode) {
        return redisTemplate.opsForValue()
                .get(feature1MetricsFreshKey(stockCode))
                .onErrorResume(ex -> {
                    log.warn("[Feature3Overlay] feature1 metrics cache read skipped. stockCode={}, cause={}",
                            stockCode, ex.getMessage());
                    return Mono.empty();
                })
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, FeatOneAnalysisMetricsDto.class));
                    } catch (Exception ex) {
                        return Mono.empty();
                    }
                });
    }

    private Mono<Boolean> writeFeature1Metrics(String stockCode, FeatOneAnalysisMetricsDto metrics) {
        if (metrics == null) {
            return Mono.just(false);
        }
        try {
            String json = objectMapper.writeValueAsString(metrics);
            return redisTemplate.opsForValue().set(feature1MetricsFreshKey(stockCode), json, FRESH_TTL)
                    .then(redisTemplate.opsForValue().set(feature1MetricsStaleKey(stockCode), json, STALE_TTL))
                    .onErrorResume(ex -> {
                        log.warn("[Feature3Overlay] feature1 metrics cache write skipped. stockCode={}, cause={}",
                                stockCode, ex.getMessage());
                        return Mono.just(false);
                    });
        } catch (Exception ex) {
            return Mono.just(false);
        }
    }

    private Mono<PortfolioAnalyzeResponseDto.OverlayInsightCard> readCachedCard(String json, String cacheStatus) {
        try {
            PortfolioAnalyzeResponseDto.OverlayInsightCard cached =
                    objectMapper.readValue(json, PortfolioAnalyzeResponseDto.OverlayInsightCard.class);
            return Mono.just(new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                    cached.overlayType(),
                    cached.title(),
                    cached.description(),
                    cached.severity(),
                    cached.source(),
                    cacheStatus,
                    cached.affectedHoldings()
            ));
        } catch (Exception ex) {
            return Mono.empty();
        }
    }

    private Mono<Boolean> writeCard(String overlay, String stockCode, PortfolioAnalyzeResponseDto.OverlayInsightCard card) {
        try {
            String json = objectMapper.writeValueAsString(card);
            return redisTemplate.opsForValue().set(freshKey(overlay, stockCode), json, FRESH_TTL)
                    .then(redisTemplate.opsForValue().set(staleKey(overlay, stockCode), json, STALE_TTL))
                    .onErrorResume(ex -> {
                        log.warn("[Feature3Overlay] overlay cache write skipped. overlay={}, stockCode={}, cause={}",
                                overlay, stockCode, ex.getMessage());
                        return Mono.just(false);
                    });
        } catch (Exception ex) {
            return Mono.just(false);
        }
    }

    private PortfolioAnalyzeResponseDto.OverlayInsightCard fallbackCard(String overlay, String cacheStatus) {
        return new PortfolioAnalyzeResponseDto.OverlayInsightCard(
                overlay,
                "보조 분석 데이터 확인 필요",
                "선택한 보조 분석 데이터를 불러오지 못해 core risk 결과만 우선 표시합니다.",
                "WARN",
                usesFeature1(overlay) ? "FEATURE1" : "FEATURE2",
                cacheStatus,
                List.of()
        );
    }

    private OverlayBundle fallbackBundle(String overlay, String cacheStatus) {
        PortfolioAnalyzeResponseDto.OverlayInsightCard card = fallbackCard(overlay, cacheStatus);
        return new OverlayBundle(List.of(card), List.of(), List.of(exposure(card, null)));
    }

    private OverlayBundle bundleFromCard(
            PortfolioAnalyzeResponseDto.OverlayInsightCard card,
            PortfolioAnalyzeRequestDto.Holding holding,
            String value
    ) {
        PortfolioAnalyzeResponseDto.HoldingOverlayRow row = holdingRow(
                holding,
                card.overlayType(),
                card.title(),
                value,
                card.severity(),
                card.source(),
                card.cacheStatus()
        );
        return new OverlayBundle(List.of(card), List.of(row), List.of(exposure(card, row)));
    }

    private OverlayBundle mergeBundles(List<OverlayBundle> bundles) {
        return new OverlayBundle(
                bundles.stream().flatMap(bundle -> bundle.cards().stream()).toList(),
                bundles.stream().flatMap(bundle -> bundle.rows().stream()).toList(),
                bundles.stream().flatMap(bundle -> bundle.exposures().stream()).toList()
        );
    }

    private List<Feature3FastApiAnalyzeRequestDto.OverlaySignal> toOverlaySignals(OverlayBundle bundle) {
        List<PortfolioAnalyzeResponseDto.HoldingOverlayRow> rows = bundle.rows();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        return rows.stream()
                .map(row -> new Feature3FastApiAnalyzeRequestDto.OverlaySignal(
                        row.stockCode(),
                        row.companyName(),
                        row.overlayType(),
                        row.label(),
                        overlayScore(row),
                        row.severity(),
                        row.source(),
                        row.value()
                ))
                .toList();
    }

    private double overlayScore(PortfolioAnalyzeResponseDto.HoldingOverlayRow row) {
        String overlay = row.overlayType();
        String value = row.value() != null ? row.value() : "";
        String severity = row.severity() != null ? row.severity() : "INFO";
        if ("fundamentals".equals(overlay)) {
            return fundamentalsScore(value, severity);
        }
        if ("technical".equals(overlay)) {
            double score = 0.0;
            if (value.contains("alignment=bullish")) score += 0.20;
            if (value.contains("alignment=bearish")) score -= 0.20;
            if (value.contains("zone=overbought")) score -= 0.10;
            if (value.contains("zone=oversold")) score += 0.10;
            return clampScore(score);
        }
        if ("correlation".equals(overlay)) {
            return 0.0;
        }
        if ("news".equals(overlay)) {
            return clampScore(parseMetric(value, "score=") * 2.0);
        }
        if ("industry".equals(overlay)) {
            return "WARN".equals(severity) ? -0.20 : 0.0;
        }
        return "WARN".equals(severity) ? -0.10 : 0.05;
    }

    private double parseMetric(String value, String prefix) {
        if (value == null || prefix == null) {
            return 0.0;
        }
        int start = value.indexOf(prefix);
        if (start < 0) {
            return 0.0;
        }
        int from = start + prefix.length();
        int to = from;
        while (to < value.length()) {
            char ch = value.charAt(to);
            if (!(Character.isDigit(ch) || ch == '-' || ch == '+' || ch == '.')) {
                break;
            }
            to++;
        }
        try {
            return Double.parseDouble(value.substring(from, to));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double fundamentalsScore(String value, String severity) {
        if (!"INFO".equals(severity) || value == null || value.isBlank()) {
            return -0.12;
        }
        double score = 0.0;
        double roe = parseMetric(value, "ROE=");
        if (roe >= 15.0) score += 0.18;
        else if (roe >= 8.0) score += 0.08;
        else if (roe > 0.0 && roe < 4.0) score -= 0.10;

        double opm = parseMetric(value, "OPM=");
        if (opm >= 15.0) score += 0.12;
        else if (opm > 0.0 && opm < 5.0) score -= 0.08;

        double debt = parseMetric(value, "Debt=");
        if (debt > 0.0 && debt <= 100.0) score += 0.08;
        else if (debt >= 200.0) score -= 0.12;

        double per = parseMetric(value, "PER=");
        if (per > 0.0 && per <= 12.0) score += 0.06;
        else if (per >= 40.0) score -= 0.08;

        double pbr = parseMetric(value, "PBR=");
        if (pbr > 0.0 && pbr <= 1.2) score += 0.04;
        else if (pbr >= 5.0) score -= 0.06;

        double revenueGrowth = parseMetric(value, "RevenueGrowth=");
        if (revenueGrowth >= 10.0) score += 0.08;
        else if (revenueGrowth < 0.0) score -= 0.08;

        return clampScore(score);
    }

    private double clampScore(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private PortfolioAnalyzeResponseDto.OverlayResult emptyOverlay() {
        return new PortfolioAnalyzeResponseDto.OverlayResult(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private List<PortfolioAnalyzeResponseDto.HoldingOverlayRow> toHoldingRows(
            List<PortfolioAnalyzeResponseDto.OverlayInsightCard> cards
    ) {
        return cards.stream()
                .flatMap(card -> card.affectedHoldings().stream()
                        .map(stockCode -> new PortfolioAnalyzeResponseDto.HoldingOverlayRow(
                                stockCode,
                                null,
                                card.overlayType(),
                                card.title(),
                                card.description(),
                                card.severity(),
                                card.source(),
                                card.cacheStatus()
                        )))
                .toList();
    }

    private List<Map<String, Object>> toAdvancedExposure(List<PortfolioAnalyzeResponseDto.OverlayInsightCard> cards) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (PortfolioAnalyzeResponseDto.OverlayInsightCard card : cards) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("overlayType", card.overlayType());
            row.put("source", card.source());
            row.put("cacheStatus", card.cacheStatus());
            row.put("affectedHoldings", card.affectedHoldings());
            rows.add(row);
        }
        return rows;
    }

    private PortfolioAnalyzeResponseDto.HoldingOverlayRow holdingRow(
            PortfolioAnalyzeRequestDto.Holding holding,
            String overlay,
            String label,
            String value,
            String severity,
            String source,
            String cacheStatus
    ) {
        return new PortfolioAnalyzeResponseDto.HoldingOverlayRow(
                holding.stockCode(),
                holding.companyName(),
                overlay,
                label,
                value,
                severity,
                source,
                cacheStatus
        );
    }

    private Map<String, Object> exposure(
            PortfolioAnalyzeResponseDto.OverlayInsightCard card,
            PortfolioAnalyzeResponseDto.HoldingOverlayRow row
    ) {
        Map<String, Object> exposure = new LinkedHashMap<>();
        exposure.put("overlayType", card.overlayType());
        exposure.put("source", card.source());
        exposure.put("cacheStatus", card.cacheStatus());
        exposure.put("severity", card.severity());
        exposure.put("affectedHoldings", card.affectedHoldings());
        if (row != null) {
            exposure.put("stockCode", row.stockCode());
            exposure.put("label", row.label());
            exposure.put("value", row.value());
        }
        return exposure;
    }

    private String overlayTitle(String overlay, PortfolioAnalyzeRequestDto.Holding holding) {
        String name = holding.companyName() != null && !holding.companyName().isBlank() ? holding.companyName() : holding.stockCode();
        return switch (overlay) {
            case "fundamentals" -> name + " 종목 건강도";
            case "industry" -> name + " 산업/Peer 편중";
            case "news" -> name + " 뉴스 흐름";
            case "correlation" -> name + " Peer 동행 참고";
            case "technical" -> name + " 기술적 지표";
            case "shortSelling" -> name + " 공매도 압력";
            case "macro" -> name + " 매크로 민감도";
            default -> name + " 보조 분석";
        };
    }

    private OverlayMetric peerClusterMetric(PeerClusterDto peerCluster) {
        if (peerCluster == null) {
            return new OverlayMetric("Peer 동행 참고", null, "Peer cluster 데이터가 부족합니다.", "WARN");
        }
        List<PeerItemDto> peers = peerCluster.getPeers();
        List<PeerItemDto> topPeers = peers == null ? List.of() : peers.stream().limit(5).toList();
        String value = !topPeers.isEmpty()
                ? String.join(" | ", topPeers.stream().map(this::peerSummary).toList())
                        + " | selectedPeers=" + safe(peerCluster.getSelectedPeerCount())
                : "selectedPeers=" + safe(peerCluster.getSelectedPeerCount());
        boolean hasHighCorr = topPeers.stream()
                .map(this::peerCorr)
                .anyMatch(corr -> corr != null && corr >= 0.75);
        return new OverlayMetric(
                "Peer 동행 참고",
                value,
                "Peer corr은 같은 업종 내 동행 종목 참고 정보입니다. 실제 비중 조정은 보유 종목 간 내부 상관관계를 기준으로 계산합니다.",
                hasHighCorr ? "WARN" : "INFO"
        );
    }

    private String peerSummary(PeerItemDto peer) {
        String name = peer.getCompanyName() != null ? peer.getCompanyName() : peer.getStockCode();
        String relation = peer.getRelation() != null ? ", relation=" + peer.getRelation().name() : "";
        String lag = peer.getBestLag() != null ? ", lag=" + peer.getBestLag() : "";
        return safe(name) + " corr=" + safe(peerCorr(peer)) + relation + lag;
    }

    private Double peerCorr(PeerItemDto peer) {
        if (peer == null) {
            return null;
        }
        return peer.getAdjustedCorr() != null ? peer.getAdjustedCorr() : peer.getCorr();
    }

    private PortfolioAnalyzeResponseDto.ExplainResult deterministicExplain(PortfolioAnalyzeResponseDto response) {
        if (response == null || response.summary() == null) {
            return null;
        }
        String text = String.format(
                "현재 포트폴리오의 연 변동성은 %.1f%%이며, 투자 성향 기준 %.1f%%와 비교해 %s 상태입니다. 주요 해석은 %s입니다.",
                value(response.summary().annualizedVolatility()) * 100,
                value(response.summary().targetVolatility()) * 100,
                response.summary().suitability(),
                response.summary().mainRiskDrivers() == null ? "없음" : String.join(", ", response.summary().mainRiskDrivers())
        );
        return new PortfolioAnalyzeResponseDto.ExplainResult(
                "DETERMINISTIC",
                null,
                text,
                new PortfolioAnalyzeResponseDto.ExplainSections(
                        new PortfolioAnalyzeResponseDto.ExplainSection("핵심 리스크", text, response.summary().mainRiskDrivers() != null ? response.summary().mainRiskDrivers() : List.of()),
                        new PortfolioAnalyzeResponseDto.ExplainSection("보조 관측", "선택한 보조 관측은 계산 결과와 분리해 참고 신호로 해석합니다.", List.of()),
                        new PortfolioAnalyzeResponseDto.ExplainSection("포트폴리오 비교", "기본 포트폴리오와 보조 관측 반영 포트폴리오를 함께 비교해 볼 수 있습니다.", List.of()),
                        new PortfolioAnalyzeResponseDto.ExplainSection("변동성 기반 분석", text, List.of()),
                        new PortfolioAnalyzeResponseDto.ExplainSection("효율성 기반 분석", "위험 대비 수익 효율은 변동성과 기대수익률을 함께 비교해 참고합니다.", List.of()),
                        new PortfolioAnalyzeResponseDto.ExplainSection("종합 판단", text, List.of())
                ),
                new PortfolioAnalyzeResponseDto.ExplainOverall(
                        text,
                        response.summary().mainRiskDrivers() != null ? response.summary().mainRiskDrivers() : List.of(),
                        response.summary().mainRiskDrivers() != null ? response.summary().mainRiskDrivers() : List.of(),
                        text
                ),
                List.of()
        );
    }

    private PortfolioAnalyzeResponseDto.ExplainResult explainOrDeterministic(PortfolioAnalyzeResponseDto response) {
        PortfolioAnalyzeResponseDto.ExplainResult explain = response != null ? response.explain() : null;
        if (explain != null && (explain.sections() != null || (explain.text() != null && !explain.text().isBlank()))) {
            return explain;
        }
        return deterministicExplain(response);
    }

    private PortfolioAnalyzeResponseDto withOverlayAndExplain(
            PortfolioAnalyzeResponseDto response,
            PortfolioAnalyzeResponseDto.OverlayResult overlays,
            PortfolioAnalyzeResponseDto.ExplainResult explain
    ) {
        return new PortfolioAnalyzeResponseDto(
                response.policy(),
                response.summary(),
                response.currentPortfolio(),
                response.basicPortfolios(),
                response.riskDrivers(),
                response.advanced(),
                overlays,
                explain,
                response.warnings(),
                response.freshness()
        );
    }

    private String freshKey(String overlay, String stockCode) {
        return "feature3:overlay:fresh:" + normalize(overlay) + ":" + normalize(stockCode);
    }

    private String staleKey(String overlay, String stockCode) {
        return "feature3:overlay:stale:" + normalize(overlay) + ":" + normalize(stockCode);
    }

    private String primaryFreshKey(String overlay, String stockCode) {
        return usesFeature1(overlay) ? feature1MetricsFreshKey(stockCode) : freshKey(overlay, stockCode);
    }

    private String primaryStaleKey(String overlay, String stockCode) {
        return usesFeature1(overlay) ? feature1MetricsStaleKey(stockCode) : staleKey(overlay, stockCode);
    }

    private String feature1MetricsFreshKey(String stockCode) {
        return "feature1:metrics:fresh:" + normalize(stockCode);
    }

    private String feature1MetricsStaleKey(String stockCode) {
        return "feature1:metrics:stale:" + normalize(stockCode);
    }

    private String normalize(String raw) {
        return raw == null ? "-" : raw.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private boolean usesFeature1(String overlay) {
        return "fundamentals".equals(overlay) || "technical".equals(overlay);
    }

    private String indicatorAvailability(IndicatorBundleDto indicators) {
        if (indicators == null) {
            return null;
        }
        List<String> available = new ArrayList<>();
        if (indicators.getEma() != null && !indicators.getEma().isEmpty()) {
            available.add("EMA");
        }
        if (indicators.getBb20_2() != null && !indicators.getBb20_2().isEmpty()) {
            available.add("BB20");
        }
        if (indicators.getStoch14_3_3() != null && !indicators.getStoch14_3_3().isEmpty()) {
            available.add("STOCH");
        }
        return available.isEmpty() ? "indicators=available" : String.join(", ", available);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private double value(Double value) {
        return value == null ? 0.0 : value;
    }

    private String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private record CachedFeature1Metrics(
            FeatOneAnalysisMetricsDto metrics,
            String cacheStatus
    ) {
    }

    private record StockIndustryContext(
            Feature2StockContext stock,
            Feature2IndustryContext industry
    ) {
    }

    private record OverlayBundle(
            List<PortfolioAnalyzeResponseDto.OverlayInsightCard> cards,
            List<PortfolioAnalyzeResponseDto.HoldingOverlayRow> rows,
            List<Map<String, Object>> exposures
    ) {
    }

    private record OverlayMetric(
            String label,
            String value,
            String description,
            String severity
    ) {
    }
}
