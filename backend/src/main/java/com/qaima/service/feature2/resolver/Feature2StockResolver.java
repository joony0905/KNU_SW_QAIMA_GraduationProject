package com.qaima.service.feature2.resolver;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.stock.StockMeta;
import com.qaima.repository.StockRepository;
import com.qaima.service.feature2.model.Feature2StockContext;
import com.qaima.service.marketdata.model.PriceSnapshot;
import com.qaima.service.marketdata.reader.PriceSnapshotReader;
import com.qaima.service.stock.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class Feature2StockResolver {

    private final PriceSnapshotReader priceSnapshotReader;
    private final StockRepository stockRepository;
    private final StockService stockService;

    public Mono<Optional<Feature2StockContext>> resolve(String stockCode, Feature2MetaDto meta) {
        return findByCode(stockCode)
                .switchIfEmpty(resolveByFallback(stockCode, meta))
                .flatMap(stock ->
                        priceSnapshotReader.getSnapshot(stock.getStockCode(), Freq.ONE_D)
                                .doOnError(ex -> log.warn(
                                        "[Feat2StockResolver] snapshot failed → fallback null. stockCode={}, cause={}",
                                        stock.getStockCode(), ex.getMessage(), ex
                                ))
                                .onErrorResume(ex -> Mono.empty())
                                .map(snapshot -> new Feature2StockContext(
                                        stock,
                                        toStockMeta(stock, snapshot)
                                ))
                                .switchIfEmpty(Mono.just(
                                        new Feature2StockContext(
                                                stock,
                                                toStockMeta(stock, null)
                                        )
                                ))
                                .map(Optional::of)
                )
                .onErrorResume(ex -> {
                    log.warn("[Feat2StockResolver] stock resolve failed. stockCode={}, cause={}",
                            stockCode, ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.STOCK_NOT_FOUND);
                    return Mono.just(Optional.empty());
                });
    }

    private Mono<Stock> findByCode(String stockCode) {
        return Mono.fromCallable(() -> stockRepository.findByStockCodeWithExchangeAndIndustry(stockCode))
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

    private StockMeta toStockMeta(Stock stock, PriceSnapshot snapshot) {

        String exchangeCode = stock.getExchange() != null ? stock.getExchange().getCode() : null;
        String countryCode = stock.getExchange() != null ? stock.getExchange().getCountry() : null;

        String sectorCode = null;
        String sectorName = null;
        String industryCode = null;
        String industryName = null;

        if (stock.getIndustry() != null) {
            industryCode = stock.getIndustry().getCode();
            industryName = stock.getIndustry().getName();

            if (stock.getIndustry().getSector() != null) {
                sectorCode = stock.getIndustry().getSector().getCode();
                sectorName = stock.getIndustry().getSector().getName();
            }
        }

        BigDecimal price = snapshot != null ? snapshot.getPrice() : null;
        BigDecimal changeRate = snapshot != null ? snapshot.getChangeRate() : null;

        if (changeRate != null) {
            changeRate = changeRate.setScale(4, RoundingMode.HALF_UP);
        }

        return StockMeta.builder()
                .stockCode(stock.getStockCode())
                .companyName(stock.getCompanyName())
                .exchangeCode(exchangeCode)
                .countryCode(countryCode)
                .currency(stock.getCurrency())
                .price(price)
                .changeRate(changeRate)
                .sectorCode(sectorCode)
                .sectorName(sectorName)
                .industryCode(industryCode)
                .industryName(industryName)
                .source("DB")
                .build();
    }
}
