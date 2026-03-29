package com.qaima.service.feature2.resolver;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Industry;
import com.qaima.domain.Stock;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.industry.IndustryMetaDto;
import com.qaima.repository.IndustryRepository;
import com.qaima.service.feature2.model.Feature2IndustryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;

/**
 * Feature2 analyze read-path 전용 Industry reader.
 * 기존 write-side IndustryResolver(upsert/insertRecover)와 역할이 다르며,
 * 본 클래스에서는 insert/upsert를 절대 수행하지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Feature2IndustryReader {

    private final IndustryRepository industryRepository;

    public Mono<Optional<Feature2IndustryContext>> resolve(Stock stock, Feature2MetaDto meta) {
        return Mono.defer(() -> {
                    if (stock == null || stock.getIndustry() == null || stock.getIndustry().getIndustryId() == null) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                        return Mono.just(Optional.<Feature2IndustryContext>empty());
                    }

                    Long industryId = stock.getIndustry().getIndustryId();
                    return Mono.fromCallable(() -> industryRepository.findById(industryId))
                            .subscribeOn(Schedulers.boundedElastic())
                            .map(opt -> opt.map(this::toContext));
                })
                .onErrorResume(ex -> {
                    log.warn("[Feature2IndustryReader] read-side industry resolve failed. stockCode={}, cause={}",
                            stock == null ? null : stock.getStockCode(), ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.INDUSTRY_MISSING);
                    return Mono.just(Optional.<Feature2IndustryContext>empty());
                });
    }

    private Feature2IndustryContext toContext(Industry industry) {
        return new Feature2IndustryContext(
                industry,
                IndustryMetaDto.builder()
                        .industryId(industry.getIndustryId())
                        .name(industry.getName())
                        .code(industry.getCode())
                        .build()
        );
    }
}
