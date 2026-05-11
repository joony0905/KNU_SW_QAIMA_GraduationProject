package com.qaima.service.feature3;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qaima.domain.Freq;
import com.qaima.dto.featone.FeatOneAnalysisResponseDto;
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
            return Mono.just(withOverlayAndExplain(response, emptyOverlay(), deterministicExplain(response)));
        }

        List<PortfolioAnalyzeRequestDto.Holding> holdings = req.holdings() != null ? req.holdings() : List.of();
        List<PortfolioAnalyzeRequestDto.Holding> targetHoldings = holdings.stream().limit(3).toList();

        return Flux.fromIterable(selected)
                .flatMap(overlay -> loadOverlay(overlay, targetHoldings)
                        .onErrorResume(ex -> {
                            log.warn("[Feature3Overlay] overlay load failed. overlay={}, cause={}", overlay, ex.getMessage(), ex);
                            return Mono.just(fallbackBundle(overlay, "MISS"));
                        }))
                .collectList()
                .map(bundles -> {
                    List<PortfolioAnalyzeResponseDto.OverlayInsightCard> cards = bundles.stream()
                            .flatMap(bundle -> bundle.cards().stream())
                            .toList();
                    List<PortfolioAnalyzeResponseDto.HoldingOverlayRow> rows = bundles.stream()
                            .flatMap(bundle -> bundle.rows().stream())
                            .toList();
                    List<Map<String, Object>> exposures = bundles.stream()
                            .flatMap(bundle -> bundle.exposures().stream())
                            .toList();
                    PortfolioAnalyzeResponseDto.OverlayResult overlayResult = new PortfolioAnalyzeResponseDto.OverlayResult(
                            cards,
                            rows,
                            exposures
                    );
                    return withOverlayAndExplain(response, overlayResult, deterministicExplain(response));
                });
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
                .flatMap(holding -> buildOverlayBundle(overlay, holding)
                        .flatMap(bundle -> writeCard(overlay, holding.stockCode(), bundle.cards().get(0)).thenReturn(bundle)))
                .collectList()
                .map(this::mergeBundles);
    }

    private Mono<OverlayBundle> buildOverlayBundle(
            String overlay,
            PortfolioAnalyzeRequestDto.Holding holding
    ) {
        if (usesFeature1(overlay)) {
            return loadFeature1Result(holding.stockCode())
                    .map(cached -> "technical".equals(overlay)
                            ? technicalBundle(holding, cached.response(), cached.cacheStatus())
                            : feature1Bundle(holding, cached.response(), cached.cacheStatus()))
                    .onErrorReturn(fallbackBundle(overlay, "MISS"));
        }

        return switch (overlay) {
            case "industry" -> loadIndustryBundle(holding).onErrorReturn(fallbackBundle(overlay, "MISS"));
            case "correlation" -> loadPeerClusterBundle(holding).onErrorReturn(fallbackBundle(overlay, "MISS"));
            case "news" -> loadNewsBundle(holding).onErrorReturn(fallbackBundle(overlay, "MISS"));
            default -> Mono.just(fallbackBundle(overlay, "MISS"));
        };
    }

    private Mono<CachedFeature1> loadFeature1Result(String stockCode) {
        return readFeature1Result(stockCode)
                .map(response -> new CachedFeature1(response, "HIT"))
                .switchIfEmpty(featOneService.getFeatOneData(
                                stockCode,
                                Freq.ONE_D,
                                null,
                                null,
                                null,
                                false,
                                null
                        )
                        .flatMap(result -> writeFeature1Result(stockCode, result.getData())
                                .thenReturn(new CachedFeature1(result.getData(), "MISS"))));
    }

    private OverlayBundle feature1Bundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            FeatOneAnalysisResponseDto response,
            String cacheStatus
    ) {
        boolean hasMetrics = response != null && response.getMetrics() != null;
        boolean hasSnapshot = hasMetrics && response.getMetrics().getMarketSnapshot() != null;
        String value = hasMetrics && response.getMetrics().getOhlcvSummary() != null
                ? "가격 관측치 " + safe(response.getMetrics().getOhlcvSummary().getCount()) + "개"
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

    private OverlayBundle technicalBundle(
            PortfolioAnalyzeRequestDto.Holding holding,
            FeatOneAnalysisResponseDto response,
            String cacheStatus
    ) {
        IndicatorBundleDto indicators = response != null && response.getMetrics() != null
                ? response.getMetrics().getIndicators()
                : null;
        String summary = response != null && response.getMetrics() != null
                ? response.getMetrics().getIndicatorSummary()
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
                metric.label(),
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
                .flatMap(json -> readCachedCard(json, "HIT"))
                .switchIfEmpty(redisTemplate.opsForValue()
                        .get(staleKey(overlay, stockCode))
                        .flatMap(json -> readCachedCard(json, "STALE")));
    }

    private Mono<FeatOneAnalysisResponseDto> readFeature1Result(String stockCode) {
        return redisTemplate.opsForValue()
                .get(feature1ResultFreshKey(stockCode))
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, FeatOneAnalysisResponseDto.class));
                    } catch (Exception ex) {
                        return Mono.empty();
                    }
                });
    }

    private Mono<Boolean> writeFeature1Result(String stockCode, FeatOneAnalysisResponseDto response) {
        if (response == null) {
            return Mono.just(false);
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            return redisTemplate.opsForValue().set(feature1ResultFreshKey(stockCode), json, FRESH_TTL)
                    .then(redisTemplate.opsForValue().set(feature1ResultStaleKey(stockCode), json, STALE_TTL));
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
                    .then(redisTemplate.opsForValue().set(staleKey(overlay, stockCode), json, STALE_TTL));
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

    private PortfolioAnalyzeResponseDto.OverlayResult emptyOverlay() {
        return new PortfolioAnalyzeResponseDto.OverlayResult(List.of(), List.of(), List.of());
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
            case "correlation" -> name + " 동행 종목 구조";
            case "technical" -> name + " 기술적 지표";
            case "shortSelling" -> name + " 공매도 압력";
            case "macro" -> name + " 매크로 민감도";
            default -> name + " 보조 분석";
        };
    }

    private OverlayMetric peerClusterMetric(PeerClusterDto peerCluster) {
        if (peerCluster == null) {
            return new OverlayMetric("동행 종목", null, "Peer cluster 데이터가 부족합니다.", "WARN");
        }
        List<PeerItemDto> peers = peerCluster.getPeers();
        PeerItemDto top = peers != null && !peers.isEmpty() ? peers.get(0) : null;
        String value = top != null
                ? safe(top.getCompanyName() != null ? top.getCompanyName() : top.getStockCode())
                        + " corr=" + safe(top.getAdjustedCorr() != null ? top.getAdjustedCorr() : top.getCorr())
                : "selectedPeers=" + safe(peerCluster.getSelectedPeerCount());
        Double topCorr = top != null ? top.getAdjustedCorr() != null ? top.getAdjustedCorr() : top.getCorr() : null;
        return new OverlayMetric(
                "동행 종목",
                value,
                "PeerCluster 엔드포인트 기반 가격 동행성 정보를 분산 제한 리스크 참고 정보로 표시합니다.",
                topCorr != null && topCorr >= 0.75 ? "WARN" : "INFO"
        );
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
                List.of()
        );
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
        return usesFeature1(overlay) ? feature1ResultFreshKey(stockCode) : freshKey(overlay, stockCode);
    }

    private String primaryStaleKey(String overlay, String stockCode) {
        return usesFeature1(overlay) ? feature1ResultStaleKey(stockCode) : staleKey(overlay, stockCode);
    }

    private String feature1ResultFreshKey(String stockCode) {
        return "feature3:feature1-result:fresh:" + normalize(stockCode);
    }

    private String feature1ResultStaleKey(String stockCode) {
        return "feature3:feature1-result:stale:" + normalize(stockCode);
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

    private record CachedFeature1(
            FeatOneAnalysisResponseDto response,
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
