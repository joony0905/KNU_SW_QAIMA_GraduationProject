package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.ShortSelling;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.feature2.Feature2MetricsDto;
import com.qaima.repository.ShortSellingRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShortSellingFeatureService {

    private final ShortSellingRepository shortSellingRepository;

    public Mono<Feature2MetricsDto.ShortSellingMetrics> loadLatest(
            Stock stock,
            Feature2MetaDto meta
    ) {
        if (stock == null) {
            meta.addWarning(Feat2WarningCode.SHORT_SELLING_MISSING);
            return Mono.empty();
        }

        return Mono.fromCallable(() -> shortSellingRepository.findTopByStockOrderByReportDateDesc(stock))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(opt -> mapOrEmpty(opt, stock, meta))
                .onErrorResume(ex -> {
                    log.warn("[ShortSellingFeatureService] load latest failed. stockCode={}, cause={}",
                            stock.getStockCode(), ex.getMessage());
                    meta.addWarning(Feat2WarningCode.SHORT_SELLING_LOAD_FAILED);
                    return Mono.empty();
                });
    }

    private Mono<Feature2MetricsDto.ShortSellingMetrics> mapOrEmpty(
            Optional<ShortSelling> opt,
            Stock stock,
            Feature2MetaDto meta
    ) {
        if (opt.isEmpty()) {
            meta.addWarning(Feat2WarningCode.SHORT_SELLING_MISSING);
            return Mono.empty();
        }

        ShortSelling entity = opt.get();
        return Mono.just(Feature2MetricsDto.ShortSellingMetrics.builder()
                .shortSellingId(entity.getShortSellingId())
                .stockId(entity.getStock() != null ? entity.getStock().getStockId() : stock.getStockId())
                .stockCode(entity.getStock() != null ? entity.getStock().getStockCode() : stock.getStockCode())
                .companyName(entity.getStock() != null ? entity.getStock().getCompanyName() : stock.getCompanyName())
                .reportDate(entity.getReportDate())
                .marketCode(entity.getMarketCode())
                .securityType(entity.getSecurityType())
                .shortVolumeTotal(entity.getShortVolumeTotal())
                .shortVolumeUptickApplied(entity.getShortVolumeUptickApplied())
                .shortVolumeUptickExempt(entity.getShortVolumeUptickExempt())
                .totalVolume(entity.getTotalVolume())
                .shortVolumeRatio(entity.getShortVolumeRatio())
                .shortAmountTotal(entity.getShortAmountTotal())
                .shortAmountUptickApplied(entity.getShortAmountUptickApplied())
                .shortAmountUptickExempt(entity.getShortAmountUptickExempt())
                .totalAmount(entity.getTotalAmount())
                .shortAmountRatio(entity.getShortAmountRatio())
                .source(entity.getSource())
                .sourceScreenId(entity.getSourceScreenId())
                .build());
    }
}
