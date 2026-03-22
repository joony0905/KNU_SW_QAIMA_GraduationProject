package com.qaima.service.feature2;

import com.qaima.common.ErrorCode;
import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.domain.Industry;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2AnalyzeRequestDto;
import com.qaima.dto.feature2.Feature2AnalyzeResponseDto;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.dto.stock.StockMeta;
import com.qaima.repository.IndustryRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.stock.StockService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature2AnalyzeService {

    private final StockService stockService;
    private final StockRepository stockRepository;
    private final IndustryRepository industryRepository;
    private final IndustryIndexService industryIndexService;
    private final ShortSellingFeatureService shortSellingFeatureService;
    private final PeerClusterService peerClusterService;

    private static final Freq INDEX_FREQ = Freq.ONE_D;
    private static final int INDEX_LIMIT = 120;

    private static final Freq PEER_FREQ = Freq.ONE_D;
    private static final int PEER_WINDOW = 90;

    public Mono<Feature2AnalyzeResponseDto> analyze(Feature2AnalyzeRequestDto req) {
        log.info("[Feat2] stockService impl={}", stockService.getClass().getName());

        Feature2MetaDto meta = Feature2MetaDto.empty();
        Feature2MetricsDto metrics = Feature2MetricsDto.empty();

        String stockCode = req == null ? null : req.getStockCode();
        if (stockCode == null || stockCode.isBlank()) {
            meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
            return Mono.just(buildResponse(metrics, meta));
        }

        log.info("[Feat2] about to call getOrCreate stockCode={}", stockCode);

        return resolveOrCreateStock(stockCode, meta)
                .flatMap(optStock -> {
                    if (optStock.isEmpty()) {
                        return Mono.just(buildResponse(metrics, meta));
                    }

                    Stock stock = optStock.get();
                    return refetchWithExchange(stock)
                            .flatMap(fullStock -> {
                                metrics.setStock(toStockMetaFromEntity(fullStock, "DB"));
                                return attachShortSellingAndContinue(fullStock, metrics, meta);
                            })
                            .onErrorResume(ex -> {
                                log.warn("[Feat2] fetch-join(exchange) re-fetch failed. code={}, cause={}",
                                        stock.getStockCode(), ex.getMessage());
                                metrics.setStock(toStockMetaFromEntity(stock, "DB"));
                                return attachShortSellingAndContinue(stock, metrics, meta);
                            });
                })
                .onErrorResume(ex -> {
                    log.warn("[Feat2AnalyzeService] analyze top-level failure. code={}, cause={}",
                            stockCode, ex.getMessage());
                    meta.addWarning(String.valueOf(ErrorCode.INTERNAL_ERROR));
                    return Mono.just(buildResponse(metrics, meta));
                });
    }

    private Mono<Feature2AnalyzeResponseDto> attachShortSellingAndContinue(
            Stock stock,
            Feature2MetricsDto metrics,
            Feature2MetaDto meta
    ) {
        return shortSellingFeatureService.loadLatest(stock, meta)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(optShortSelling -> {
                    optShortSelling.ifPresent(metrics::setShortSelling);
                    return resolveIndustryAndAttachDownstream(stock, metrics, meta);
                });
    }

    private Mono<Feature2AnalyzeResponseDto> resolveIndustryAndAttachDownstream(
            Stock stock,
            Feature2MetricsDto metrics,
            Feature2MetaDto meta
    ) {
        return resolveIndustryIdSafe(stock, meta)
                .flatMap(optIndustryId -> {
                    if (optIndustryId.isEmpty()) {
                        return Mono.just(buildResponse(metrics, meta));
                    }

                    Long industryId = optIndustryId.get();
                    return Mono.fromCallable(() -> industryRepository.findById(industryId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(optIndustry -> {
                                if (optIndustry.isEmpty()) {
                                    meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                    return Mono.just(buildResponse(metrics, meta));
                                }

                                Industry industry = optIndustry.get();
                                metrics.setIndustry(toIndustryMeta(industry));

                                return industryIndexService
                                        .loadIndustryIndex(industry.getIndustryId(), meta, INDEX_FREQ, INDEX_LIMIT)
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty())
                                        .flatMap(optIndexBlock -> {
                                            optIndexBlock.ifPresent(metrics::setIndustryIndex);

                                            return peerClusterService.getPeerCluster(
                                                            industry.getIndustryId(),
                                                            stock.getStockCode(),
                                                            PEER_FREQ,
                                                            PEER_WINDOW,
                                                            8,
                                                            5
                                                    )
                                                    .map(result -> {
                                                        metrics.setPeerCluster(result.getPeerCluster());
                                                        if (result.getWarnings() != null && !result.getWarnings().isEmpty()) {
                                                            meta.getWarnings().addAll(result.getWarnings());
                                                        }
                                                        return buildResponse(metrics, meta);
                                                    })
                                                    .onErrorResume(ex -> {
                                                        log.warn("[Feat2] peerCluster load failed. industryId={}, cause={}",
                                                                industry.getIndustryId(), ex.getMessage());
                                                        meta.addWarning(Feat2WarningCode.PEER_CLUSTER_MISSING);
                                                        metrics.setPeerCluster(null);
                                                        return Mono.just(buildResponse(metrics, meta));
                                                    });
                                        });
                            })
                            .onErrorResume(ex -> {
                                log.warn("[Feat2] industry fetch failed. code={}, cause={}",
                                        stock.getStockCode(), ex.getMessage());
                                meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                return Mono.just(buildResponse(metrics, meta));
                            });
                });
    }

    private Mono<Optional<Stock>> resolveOrCreateStock(String rawStockCode, Feature2MetaDto meta) {
        return stockService.getOrCreateStockByCode(rawStockCode)
                .map(Optional::of)
                .onErrorResume(ex -> {
                    log.warn("[Feat2] stock resolve/create failed -> warnings only. rawCode={}, cause={}",
                            rawStockCode, ex.getMessage());
                    meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
                    meta.addWarning(Feat2WarningCode.EXTERNAL_API_FALLBACK_USED);
                    return Mono.just(Optional.empty());
                });
    }

    private Mono<Stock> refetchWithExchange(Stock stock) {
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(stock.getStockCode()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(opt -> opt.orElse(stock))
                .onErrorResume(ex -> Mono.just(stock));
    }

    private Mono<Optional<Long>> resolveIndustryIdSafe(Stock stock, Feature2MetaDto meta) {
        try {
            if (stock.getIndustry() == null || stock.getIndustry().getIndustryId() == null) {
                meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                return Mono.just(Optional.empty());
            }
            return Mono.just(Optional.of(stock.getIndustry().getIndustryId()));
        } catch (Exception ex) {
            meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
            return Mono.just(Optional.empty());
        }
    }

    private Feature2AnalyzeResponseDto buildResponse(Feature2MetricsDto metrics, Feature2MetaDto meta) {
        return Feature2AnalyzeResponseDto.builder()
                .metrics(metrics)
                .explain(null)
                .meta(meta)
                .build();
    }

    private StockMeta toStockMetaFromEntity(Stock stock, String source) {
        String exchangeCode = stock.getExchange() != null ? stock.getExchange().getCode() : null;
        String countryCode = stock.getExchange() != null ? stock.getExchange().getCountry() : null;

        return StockMeta.builder()
                .stockCode(stock.getStockCode())
                .companyName(stock.getCompanyName())
                .exchangeCode(exchangeCode)
                .countryCode(countryCode)
                .currency(stock.getCurrency())
                .price(null)
                .changeRate(null)
                .source(source)
                .build();
    }

    private IndustryMetaDto toIndustryMeta(Industry industry) {
        return IndustryMetaDto.builder()
                .industryId(industry.getIndustryId())
                .name(industry.getName())
                .code(industry.getCode())
                .build();
    }
}
