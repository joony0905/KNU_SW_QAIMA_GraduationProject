package com.qaima.service.feature2.resolver;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.stock.StockMeta;
import com.qaima.repository.StockRepository;
import com.qaima.service.feature2.model.Feature2StockContext;
import com.qaima.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class Feature2StockResolver {

    private final StockRepository stockRepository;
    private final StockService stockService;

    public Mono<Optional<Feature2StockContext>> resolve(String stockCode, Feature2MetaDto meta) {
        return findByCode(stockCode)
                .switchIfEmpty(resolveByFallback(stockCode, meta))
                .map(stock -> Optional.of(new Feature2StockContext(stock, toStockMeta(stock, "DB"))))
                .onErrorResume(ex -> {
                    log.warn("[Feat2StockResolver] stock resolve failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
                    return Mono.just(Optional.empty());
                });
    }

    private Mono<Stock> findByCode(String stockCode) {
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchange(stockCode))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> opt.map(Mono::just).orElseGet(Mono::empty));
    }

    private Mono<Stock> resolveByFallback(String stockCode, Feature2MetaDto meta) {
        return stockService.getOrCreateStockByCode(stockCode)
                .doOnNext(ignore -> meta.addWarning(Feat2WarningCode.EXTERNAL_API_FALLBACK_USED))
                .flatMap(created -> Mono.fromCallable(() ->
                                stockRepository.findByIdWithExchangeAndIndustry(created.getStockId())
                        )
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(opt -> opt.map(Mono::just).orElseGet(() -> Mono.just(created)))
                )
                .onErrorResume(ex -> {
                    log.warn("[Feat2StockResolver] stock fallback(getOrCreate) failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
                    return Mono.empty();
                });
    }

    private StockMeta toStockMeta(Stock stock, String source) {
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
}
