package com.qaima.service.feature2;

import com.qaima.common.ErrorCode;
import com.qaima.common.Feat2WarningCode;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.feature2.model.Feature2Command;
import com.qaima.service.feature2.model.Feature2IndustryContext;
import com.qaima.service.feature2.model.Feature2StockContext;
import com.qaima.service.feature2.resolver.Feature2IndustryReader;
import com.qaima.service.feature2.resolver.Feature2StockResolver;
import com.qaima.service.feature2.support.Feature2MetricsAssembler;
import com.qaima.service.feature2.support.Feature2RequestNormalizer;
import com.qaima.service.feature2.support.Feature2ResponseFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature2AnalyzeService {

    private final Feature2RequestNormalizer requestNormalizer;
    private final Feature2StockResolver stockResolver;
    private final Feature2IndustryReader industryReader;
    private final Feature2MetricsAssembler metricsAssembler;
    private final Feature2ResponseFactory responseFactory;

    private final IndustryIndexService industryIndexService;
    private final ShortSellingFeatureService shortSellingFeatureService;
    private final PeerClusterService peerClusterService;
    private final BaseRateFeatureService baseRateFeatureService;
    private final NewsSentimentService newsSentimentService;

    public Mono<Feature2AnalyzeResponseDto> analyze(Feature2AnalyzeRequestDto req) {
        final Feature2MetaDto meta = Feature2MetaDto.empty();
        final Feature2MetricsDto metrics = metricsAssembler.empty();
        final Feature2Command command = requestNormalizer.normalize(req);

        log.info("[Feature2][service-start] incoming req={}, normalized stockCode={}, freq={}, window={}, peerCount={}, maxLag={}",
                req, command.stockCode(), command.freq(), command.window(), command.peerCount(), command.maxLag());
        log.info("[Feat2] analyze start. stockCode={}, freq={}, window={}, peerCount={}, maxLag={}",
                command.stockCode(), command.freq(), command.window(), command.peerCount(), command.maxLag());

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
                            .then(attachShortSelling(stockContext, meta, metrics))
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
                                        .then(attachNews(stockContext, meta, metrics))
                                        .then(Mono.fromSupplier(() -> responseFactory.success(metrics, meta)));
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
                        command.peerCount(),
                        command.maxLag()
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
                    Optional.ofNullable(result.getWarnings())
                            .orElseGet(List::of)
                            .forEach(meta::addWarning);
                })
                .then();
    }
}
