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

    // MVP 고정 파라미터
    private static final Freq INDEX_FREQ = Freq.ONE_D;
    private static final int INDEX_LIMIT = 120;

    private static final Freq PEER_FREQ = Freq.ONE_D;
    private static final int PEER_WINDOW = 90;

    private final StockService stockService;              // Mono<Stock> getOrCreateStockByCode(...)
    private final StockRepository stockRepository;        // JPA (blocking)
    private final IndustryRepository industryRepository;  // JPA (blocking)
    private final IndustryIndexService industryIndexService;
    private final ShortSellingFeatureService shortSellingFeatureService;
    private final PeerClusterService peerClusterService;  // Redis only (현재)
    private final BaseRateFeatureService baseRateFeatureService;

    public Mono<Feature2AnalyzeResponseDto> analyze(Feature2AnalyzeRequestDto req) {
        log.info("[Feat2] stockService impl={}", stockService.getClass().getName());

        final Feature2MetaDto meta = Feature2MetaDto.empty();
        final Feature2MetricsDto metrics = Feature2MetricsDto.empty();

        final String stockCode = req == null ? null : req.getStockCode();
        if (stockCode == null || stockCode.isBlank()) {
            meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
            return Mono.just(buildResponse(metrics, meta));
        }

        log.info("[Feat2] about to call getOrCreate stockCode={}", stockCode);

        // 1) Stock resolve/upsert (DB miss -> 외부 메타 조회 -> 저장)
        return resolveOrCreateStock(stockCode, meta)
                .flatMap(optStock -> {
                    if (optStock.isEmpty()) {
                        return Mono.just(buildResponse(metrics, meta));
                    }

                    final Stock stock = optStock.get();

                    // 1-1) exchange fetch join 재조회 (안전)
                    return refetchWithExchange(stock)
                            .flatMap(resolved -> attachMetricsAndAnalyze(resolved, metrics, meta))
                            .onErrorResume(ex -> {
                                // exchange 재조회 실패는 부분 성공 유지
                                log.warn("[Feat2] fetch-join(exchange) re-fetch failed. code={}, cause={}",
                                        stock.getStockCode(), ex.getMessage());

                                // 최소한 stock 메타는 채우고, baseRate/shortSelling/industry/index/peer는 가능한 만큼 시도
                                return attachMetricsAndAnalyze(stock, metrics, meta);
                            });
                })
                .onErrorResume(ex -> {
                    // 최상위 보호막 (절대 throw 안 함)
                    log.warn("[Feat2AnalyzeService] analyze top-level failure. code={}, cause={}",
                            stockCode, ex.getMessage());
                    meta.addWarning(String.valueOf(ErrorCode.INTERNAL_ERROR));
                    return Mono.just(buildResponse(metrics, meta));
                });
    }

    private Mono<Feature2AnalyzeResponseDto> attachMetricsAndAnalyze(
            Stock stock,
            Feature2MetricsDto metrics,
            Feature2MetaDto meta
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
                                return resolveIndustryAndAttachDownstream(stock, metrics, meta);
                            });
                });
    }

    private Mono<Feature2AnalyzeResponseDto> resolveIndustryAndAttachDownstream(
            Stock stock,
            Feature2MetricsDto metrics,
            Feature2MetaDto meta
    ) {
        /**
         * Industry resolve + (IndustryIndex -> PeerCluster)까지 붙이는 공용 흐름
         * - 여기서부터는 "가능한 만큼 채우고" 마지막에 response를 한 번만 만든다.
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
                                        .loadIndustryIndex(industry.getIndustryId(), meta, INDEX_FREQ, INDEX_LIMIT)
                                        .map(Optional::of)
                                        .defaultIfEmpty(Optional.empty())
                                        .flatMap(indexBlockOpt -> {
                                            indexBlockOpt.ifPresent(metrics::setIndustryIndex);

                                            // =========================
                                            // Feature2: Peer Cluster 연결 (Redis only)
                                            // =========================
                                            return peerClusterService.getPeerCluster(
                                                            industry.getIndustryId(),
                                                            stock.getStockCode(),
                                                            PEER_FREQ,
                                                            PEER_WINDOW,
                                                            8,
                                                            5
                                                    )
                                                    .map(result -> {
                                                        metrics.setPeerCluster(result.getPeerCluster()); // nullable OK
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
                            rawStockCode, ex.getMessage());
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
                .onErrorResume(ex -> Mono.just(stock));
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
        return Feature2AnalyzeResponseDto.builder()
                .metrics(metrics)  // 항상 존재
                .explain(null)     // FR-21 includeExplain 붙일 때만
                .meta(meta)        // warnings 누적
                .build();
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
