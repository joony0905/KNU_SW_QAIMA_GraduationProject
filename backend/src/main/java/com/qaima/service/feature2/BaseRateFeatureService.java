package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.BaseRate;
import com.qaima.domain.Exchange;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.service.baserate.BaseRateSyncService;
import com.qaima.service.baserate.FredBaseRateSyncService;
import com.qaima.service.feature2.model.Feature2StockContext;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class BaseRateFeatureService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final BaseRateSyncService baseRateSyncService;
    private final FredBaseRateSyncService fredBaseRateSyncService;

    public Mono<Feature2MetricsDto.BaseRateMetrics> loadLatest(Feature2MetaDto meta) {
        return loadKrLatest(meta);
    }

    public Mono<Feature2MetricsDto.BaseRateMetrics> loadLatest(
            Feature2StockContext stockContext,
            Feature2MetaDto meta
    ) {
        if (isUsStock(stockContext)) {
            return loadUsLatest(meta);
        }
        return loadKrLatest(meta);
    }

    private Mono<Feature2MetricsDto.BaseRateMetrics> loadKrLatest(Feature2MetaDto meta) {
        LocalDate today = LocalDate.now(SEOUL);

        return baseRateSyncService.ensureDailySynced(today)
                .map(baseRate -> toMetrics(baseRate, "KR"))
                .switchIfEmpty(Mono.defer(() -> {
                    meta.addWarning(Feat2WarningCode.BASE_RATE_MISSING);
                    return Mono.empty();
                }))
                .onErrorResume(ex -> {
                    log.warn("[Feat2] baseRate load failed. cause={}", ex.getMessage());
                    meta.addWarning(Feat2WarningCode.BASE_RATE_LOAD_FAILED);
                    return baseRateSyncService.findLatest(today)
                            .map(baseRate -> toMetrics(baseRate, "KR"))
                            .switchIfEmpty(Mono.defer(() -> {
                                meta.addWarning(Feat2WarningCode.BASE_RATE_MISSING);
                                return Mono.empty();
                            }))
                            .cast(Feature2MetricsDto.BaseRateMetrics.class);
                });
    }

    private Mono<Feature2MetricsDto.BaseRateMetrics> loadUsLatest(Feature2MetaDto meta) {
        LocalDate today = LocalDate.now(SEOUL);

        return fredBaseRateSyncService.ensureSynced(today)
                .map(baseRate -> toMetrics(baseRate, "US"))
                .switchIfEmpty(Mono.defer(() -> {
                    meta.addWarning(Feat2WarningCode.BASE_RATE_MISSING);
                    return Mono.empty();
                }))
                .onErrorResume(ex -> {
                    log.warn("[Feat2] US baseRate load failed. cause={}", ex.getMessage());
                    meta.addWarning(Feat2WarningCode.BASE_RATE_LOAD_FAILED);
                    return fredBaseRateSyncService.findLatest(today)
                            .map(baseRate -> toMetrics(baseRate, "US"))
                            .switchIfEmpty(Mono.defer(() -> {
                                meta.addWarning(Feat2WarningCode.BASE_RATE_MISSING);
                                return Mono.empty();
                            }))
                            .cast(Feature2MetricsDto.BaseRateMetrics.class);
                });
    }

    private Feature2MetricsDto.BaseRateMetrics toMetrics(BaseRate baseRate, String countryCode) {
        return Feature2MetricsDto.BaseRateMetrics.builder()
                .date(baseRate.getBaseDate())
                .value(baseRate.getRateValue())
                .unit(baseRate.getUnitName())
                .source(baseRate.getSource())
                .countryCode(countryCode)
                .statCode(baseRate.getStatCode())
                .itemCode(baseRate.getItemCode())
                .build();
    }

    private boolean isUsStock(Feature2StockContext stockContext) {
        if (stockContext == null) {
            return false;
        }
        Stock stock = stockContext.stock();
        if (stock == null) {
            return false;
        }
        Exchange exchange = stock.getExchange();
        if (exchange == null) {
            return false;
        }
        String country = exchange.getCountry();
        if ("US".equalsIgnoreCase(country)) {
            return true;
        }
        String exchangeCode = exchange.getCode();
        return "NASDAQ".equalsIgnoreCase(exchangeCode) || "NYSE".equalsIgnoreCase(exchangeCode);
    }
}
