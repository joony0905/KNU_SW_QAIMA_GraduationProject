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
import com.qaima.dto.peercluster.PeerClusterDto;
import com.qaima.dto.stock.StockMeta;
import com.qaima.repository.IndustryRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature2AnalyzeService {

    // ===== 기본값 (MVP) =====
    private static final Freq DEFAULT_FREQ = Freq.ONE_D;
    private static final int DEFAULT_WINDOW = 90;
    private static final int DEFAULT_PEER_COUNT = 8;
    private static final int DEFAULT_MAX_LAG = 5;

    // ===== 안전 범위 =====
    private static final int MIN_WINDOW = 20;
    private static final int MAX_WINDOW = 365;

    private static final int MIN_PEER_COUNT = 3;
    private static final int MAX_PEER_COUNT = 20;

    private static final int MIN_MAX_LAG = 1;
    private static final int MAX_MAX_LAG = 20;

    private final StockService stockService;              // Mono<Stock> getOrCreateStockByCode(...)
    private final StockRepository stockRepository;        // JPA (blocking)
    private final IndustryRepository industryRepository;  // JPA (blocking)
    private final IndustryIndexService industryIndexService;
    private final ShortSellingFeatureService shortSellingFeatureService;
    private final PeerClusterService peerClusterService;
    private final BaseRateFeatureService baseRateFeatureService;

    public Mono<Feature2AnalyzeResponseDto> analyze(Feature2AnalyzeRequestDto req) {
        log.info("[Feat2] stockService impl={}", stockService.getClass().getName());

        final Feature2MetaDto meta = Feature2MetaDto.empty();
        final Feature2MetricsDto metrics = Feature2MetricsDto.empty();

        final String stockCode = normalizeStockCode(req == null ? null : req.getStockCode());
        final Freq freq = normalizeFreq(req == null ? null : req.getFreq());
        final int window = normalizeWindow(req == null ? null : req.getWindow());
        final int peerCount = normalizePeerCount(req == null ? null : req.getPeerCount());
        final int maxLag = normalizeMaxLag(req == null ? null : req.getMaxLag());

        if (stockCode == null || stockCode.isBlank()) {
            meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
            return Mono.just(buildResponse(metrics, meta));
        }

        log.info("[Feat2] about to call getOrCreate stockCode={}, freq={}, window={}, peerCount={}, maxLag={}",
                stockCode, freq, window, peerCount, maxLag);

        // 1) Stock resolve/upsert (DB miss -> 외부 메타 조회 -> 저장)
        return resolveOrCreateStock(stockCode, meta)
                .flatMap(optStock -> {
                    if (optStock.isEmpty()) {
                        return Mono.just(buildResponse(metrics, meta));
                    }

                    final Stock stock = optStock.get();

                    // 1-1) exchange fetch join 재조회 (안전)
                    return refetchWithExchange(stock)
                            .flatMap(resolved -> attachMetricsAndAnalyze(
                                    resolved,
                                    metrics,
                                    meta,
                                    freq,
                                    window,
                                    peerCount,
                                    maxLag
                            ))
                            .onErrorResume(ex -> {
                                // exchange 재조회 실패는 부분 성공 유지
                                log.warn("[Feat2] fetch-join(exchange) re-fetch failed. code={}, cause={}",
                                        stock.getStockCode(), ex.getMessage());

                                // 최소한 stock 메타는 채우고, downstream은 가능한 만큼 시도
                                return attachMetricsAndAnalyze(
                                        stock,
                                        metrics,
                                        meta,
                                        freq,
                                        window,
                                        peerCount,
                                        maxLag
                                );
                            });
                })
                .onErrorResume(ex -> {
                    // 최상위 보호막 (절대 throw 안 함)
                    log.warn("[Feat2AnalyzeService] analyze top-level failure. code={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    meta.addWarning(String.valueOf(ErrorCode.INTERNAL_ERROR));
                    return Mono.just(buildResponse(metrics, meta));
                });
    }

    private Mono<Feature2AnalyzeResponseDto> attachMetricsAndAnalyze(
            Stock stock,
            Feature2MetricsDto metrics,
            Feature2MetaDto meta,
            Freq freq,
            int window,
            int peerCount,
            int maxLag
    ) {
        metrics.setStock(toStockMetaFromEntity(stock, "DB"));

        // 1-2) Base Rate 연결
        return baseRateFeatureService.loadLatest(meta)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(baseRateOpt -> {
                    baseRateOpt.ifPresent(metrics::setBaseRate);

                    // 1-3) Short Selling 연결
                    return shortSellingFeatureService.loadLatest(stock, meta)
                            .map(Optional::of)
                            .defaultIfEmpty(Optional.empty())
                            .flatMap(shortSellingOpt -> {
                                shortSellingOpt.ifPresent(metrics::setShortSelling);
                                return resolveIndustryAndAttachDownstream(
                                        stock,
                                        metrics,
                                        meta,
                                        freq,
                                        window,
                                        peerCount,
                                        maxLag
                                );
                            });
                });
    }

    private Mono<Feature2AnalyzeResponseDto> resolveIndustryAndAttachDownstream(
            Stock stock,
            Feature2MetricsDto metrics,
            Feature2MetaDto meta,
            Freq freq,
            int window,
            int peerCount,
            int maxLag
    ) {
        /**
         * Industry resolve + (IndustryIndex -> PeerCluster)까지 붙이는 공용 흐름
         * - 여기서부터는 가능한 만큼 채우고 마지막에 response를 한 번만 만든다.
         */

        // 2) Industry resolve (id만 안전하게 추출 후 재조회)
        return resolveIndustryIdSafe(stock, meta)
                .flatMap(optIndustryId -> {
                    if (optIndustryId.isEmpty()) {
                        return Mono.just(buildResponse(metrics, meta));
                    }

                    final Long industryId = optIndustryId.get();

                    // Industry 엔티티 재조회 (blocking -> boundedElastic)
                    return Mono.fromCallable(() -> industryRepository.findById(industryId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(optIndustry -> {
                                if (optIndustry.isEmpty()) {
                                    meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                    return Mono.just(buildResponse(metrics, meta));
                                }

                                Industry industry = optIndustry.get();
                                metrics.setIndustry(toIndustryMeta(industry));

                                // =========================
                                // Feature2: Industry Index 연결
                                // =========================
                                return industryIndexService
                                        .loadIndustryIndex(industry.getIndustryId(), meta, freq, window)
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty())
                                        .flatMap(indexBlockOpt -> {
                                            indexBlockOpt.ifPresent(metrics::setIndustryIndex);

                                            // =========================
                                            // Feature2: Peer Cluster 연결
                                            // =========================
                                            return peerClusterService.getPeerCluster(
                                                            industry.getIndustryId(),
                                                            stock.getStockCode(),
                                                            freq,
                                                            window,
                                                            peerCount,
                                                            maxLag
                                                    )
                                                    .map(result -> {
                                                        PeerClusterDto pc = result.getPeerCluster();

                                                        if (pc != null) {
                                                            metrics.setPeerCluster(pc);
                                                        }

                                                        if (result.getWarnings() != null) {
                                                            result.getWarnings().forEach(meta::addWarning);
                                                        }

                                                        return buildResponse(metrics, meta);
                                                    })
                                                    .onErrorResume(ex -> {
                                                        log.warn("[Feat2] peerCluster load failed. industryId={}, stockCode={}, cause={}",
                                                                industry.getIndustryId(), stock.getStockCode(), ex.getMessage(), ex);
                                                        meta.addWarning(Feat2WarningCode.PEER_CLUSTER_MISSING);
                                                        metrics.setPeerCluster(null);
                                                        return Mono.just(buildResponse(metrics, meta));
                                                    });
                                        })
                                        .onErrorResume(ex -> {
                                            log.warn("[Feat2] industryIndex load failed. industryId={}, cause={}",
                                                    industry.getIndustryId(), ex.getMessage(), ex);
                                            // loadIndustryIndex 내부에서 meta warning을 넣더라도
                                            // 여기서는 부분 성공 유지용 보호막만 둔다.
                                            return Mono.just(buildResponse(metrics, meta));
                                        });
                            })
                            .onErrorResume(ex -> {
                                log.warn("[Feat2] industry fetch failed. code={}, cause={}",
                                        stock.getStockCode(), ex.getMessage(), ex);
                                meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                return Mono.just(buildResponse(metrics, meta));
                            });
                });
    }

    /**
     * Stock resolve/upsert:
     * - StockService는 Mono<Stock>을 반환 (내부에서 boundedElastic 처리 포함)
     * - Feature2에서는 throw 금지 -> warning으로 전환
     */
    private Mono<Optional<Stock>> resolveOrCreateStock(String rawStockCode, Feature2MetaDto meta) {
        return stockService.getOrCreateStockByCode(rawStockCode)
                .map(Optional::of)
                .onErrorResume(ex -> {
                    log.warn("[Feat2] stock resolve/create failed -> warnings only. rawCode={}, cause={}",
                            rawStockCode, ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
                    // MVP에서는 "외부 메타 조회 경로에서 문제 발생" 정도의 알림 용도
                    meta.addWarning(Feat2WarningCode.EXTERNAL_API_FALLBACK_USED);
                    return Mono.just(Optional.empty());
                });
    }

    /**
     * exchange fetch join 재조회:
     * - StockService에서 이미 exchange를 fetch join으로 가져왔을 수도 있지만,
     *   이후 레이어에서 안전하게 쓰기 위해 한 번 더 보강
     */
    private Mono<Stock> refetchWithExchange(Stock stock) {
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(stock.getStockCode()))
                .subscribeOn(Schedulers.boundedElastic())
                .map(opt -> opt.orElse(stock))
                .onErrorResume(ex -> {
                    log.warn("[Feat2] refetchWithExchange failed. stockCode={}, cause={}",
                            stock.getStockCode(), ex.getMessage(), ex);
                    return Mono.just(stock);
                });
    }

    /**
     * LAZY 안전: Industry ID만 뽑아내기
     */
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
        List<String> dedupedWarnings = meta.getWarnings() == null
                ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(meta.getWarnings()));

        Feature2MetaDto normalizedMeta = Feature2MetaDto.builder()
                .warnings(dedupedWarnings)
                .build();

        return Feature2AnalyzeResponseDto.builder()
                .metrics(metrics)
                .explain(null)
                .meta(normalizedMeta)
                .build();
    }

    /* =========================
       Normalize
       ========================= */
    private String normalizeStockCode(String stockCode) {
        if (stockCode == null) {
            return null;
        }
        String trimmed = stockCode.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Freq normalizeFreq(Freq freq) {
        return freq != null ? freq : DEFAULT_FREQ;
    }

    private int normalizeWindow(Integer window) {
        if (window == null) {
            return DEFAULT_WINDOW;
        }
        return Math.max(MIN_WINDOW, Math.min(MAX_WINDOW, window));
    }

    private int normalizePeerCount(Integer peerCount) {
        if (peerCount == null) {
            return DEFAULT_PEER_COUNT;
        }
        return Math.max(MIN_PEER_COUNT, Math.min(MAX_PEER_COUNT, peerCount));
    }

    private int normalizeMaxLag(Integer maxLag) {
        if (maxLag == null) {
            return DEFAULT_MAX_LAG;
        }
        return Math.max(MIN_MAX_LAG, Math.min(MAX_MAX_LAG, maxLag));
    }

    /* =========================
       Mapping
       ========================= */
    private StockMeta toStockMetaFromEntity(Stock stock, String source) {
        String exchangeCode = stock.getExchange() != null ? stock.getExchange().getCode() : null;
        String countryCode = stock.getExchange() != null ? stock.getExchange().getCountry() : null;

        return StockMeta.builder()
                .stockCode(stock.getStockCode())
                .companyName(stock.getCompanyName())
                .exchangeCode(exchangeCode)
                .countryCode(countryCode)
                .currency(stock.getCurrency())
                // MVP: realtime은 stock_realtime_cache로 채울 예정
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