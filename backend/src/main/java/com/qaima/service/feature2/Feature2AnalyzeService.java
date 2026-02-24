// backend/src/main/java/com/qaima/service/feature2/Feature2AnalyzeService.java
package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature2AnalyzeService {

    private final StockService stockService;              // Mono<Stock> getOrCreateStockByCode(...)
    private final StockRepository stockRepository;        // JPA (blocking)
    private final IndustryRepository industryRepository;  // JPA (blocking)

    public Mono<Feature2AnalyzeResponseDto> analyze(Feature2AnalyzeRequestDto req) {
        log.info("[Feat2] stockService impl={}", stockService.getClass().getName());
        log.info("[Feat2] about to call getOrCreate stockCode={}", req.getStockCode());
        Feature2MetaDto meta = Feature2MetaDto.empty();
        Feature2MetricsDto metrics = Feature2MetricsDto.empty();

        String stockCode = (req == null) ? null : req.getStockCode();
        if (stockCode == null || stockCode.isBlank()) {
            meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
            return Mono.just(buildResponse(metrics, meta));
        }

        // 1) Stock resolve/upsert (DB miss -> 외부 메타 -> 저장) : StockService가 Mono로 제공
        return resolveOrCreateStock(stockCode, meta)
                .flatMap(optStock -> {
                    if (optStock.isEmpty()) {
                        return Mono.just(buildResponse(metrics, meta));
                    }

                    Stock stock = optStock.get();

                    // 1-1) exchange fetch join 재조회 (안전)
                    return refetchWithExchange(stock)
                            .flatMap(s -> {
                                metrics.setStock(toStockMetaFromEntity(s, "DB"));

                                // 2) Industry resolve (id만 안전하게 추출 후 재조회)
                                return resolveIndustryIdSafe(s, meta)
                                        .flatMap(optIndustryId -> {
                                            if (optIndustryId.isEmpty()) {
                                                return Mono.just(buildResponse(metrics, meta));
                                            }
                                            Long industryId = optIndustryId.get();

                                            return Mono.fromCallable(() -> industryRepository.findById(industryId))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .map(optIndustry -> {
                                                        if (optIndustry.isEmpty()) {
                                                            meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                                        } else {
                                                            metrics.setIndustry(toIndustryMeta(optIndustry.get()));
                                                        }
                                                        return buildResponse(metrics, meta);
                                                    })
                                                    .onErrorResume(ex -> {
                                                        meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                                        return Mono.just(buildResponse(metrics, meta));
                                                    });
                                        });
                            })
                            .onErrorResume(ex -> {
                                // exchange 재조회 실패는 부분 성공 유지
                                log.warn("[Feature2] fetch-join(exchange) re-fetch failed. code={}, cause={}",
                                        stock.getStockCode(), ex.getMessage());
                                metrics.setStock(toStockMetaFromEntity(stock, "DB"));
                                // industry까지는 시도(가능하면)
                                return resolveIndustryIdSafe(stock, meta)
                                        .flatMap(optIndustryId -> {
                                            if (optIndustryId.isEmpty()) {
                                                return Mono.just(buildResponse(metrics, meta));
                                            }
                                            Long industryId = optIndustryId.get();
                                            return Mono.fromCallable(() -> industryRepository.findById(industryId))
                                                    .subscribeOn(Schedulers.boundedElastic())
                                                    .map(optIndustry -> {
                                                        if (optIndustry.isEmpty()) {
                                                            meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                                        } else {
                                                            metrics.setIndustry(toIndustryMeta(optIndustry.get()));
                                                        }
                                                        return buildResponse(metrics, meta);
                                                    })
                                                    .onErrorResume(e2 -> {
                                                        meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                                                        return Mono.just(buildResponse(metrics, meta));
                                                    });
                                        });
                            });
                });
    }

    /**
     * Stock resolve/upsert:
     * - StockService는 Mono<Stock>을 반환 (내부에서 boundedElastic 처리 포함)
     * - Feature2에서는 throw 금지 → warning으로 전환
     */
    private Mono<Optional<Stock>> resolveOrCreateStock(String rawStockCode, Feature2MetaDto meta) {
        return stockService.getOrCreateStockByCode(rawStockCode)
                .map(Optional::of)
                .onErrorResume(ex -> {
                    log.warn("[Feature2] stock resolve/create failed -> warnings only. rawCode={}, cause={}",
                            rawStockCode, ex.getMessage());
                    meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);

                    // NOTE: 현재 구조상 "fallback 사용"을 정확히 판정하기 어려움.
                    // MVP에서는 "외부 메타 조회 경로에서 문제 발생" 정도로 알림 용도.
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
                .metrics(metrics)
                .explain(null) // FR-21 includeExplain 붙일 때만
                .meta(meta)
                .build();
    }

    /* =========================
       Mapping
       ========================= */

    private StockMeta toStockMetaFromEntity(Stock stock, String source) {
        String exchangeCode = (stock.getExchange() != null) ? stock.getExchange().getCode() : null;
        String countryCode  = (stock.getExchange() != null) ? stock.getExchange().getCountry() : null;

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