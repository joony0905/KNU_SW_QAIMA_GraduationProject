package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.repository.IndustryIndexMapRepository;
import com.qaima.repository.IndustryIndexRepository;
import com.qaima.service.marketdata.reader.IndustryIndexReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndustryIndexService {

    private final IndustryIndexMapRepository indexMapRepository;
    private final IndustryIndexRepository indexRepository;
    private final IndustryIndexReader industryIndexReader;

    public Mono<IndustryIndexBlockDto> loadIndustryIndex(
            Long industryId,
            Feature2MetaDto meta,
            Freq freq,
            int window
    ) {
        if (industryId == null) {
            meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
            return Mono.empty();
        }

        return Mono.fromCallable(() ->
                        indexMapRepository.findFirstByIdIndustryId(industryId)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optMap -> {
                    if (optMap.isEmpty()) {
                        log.warn("[IndustryIndexService] indexMap missing. industryId={}", industryId);
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                        return Mono.empty();
                    }
                    return Mono.just(optMap.get());
                })
                .flatMap(map ->
                        Mono.fromCallable(() ->
                                        indexRepository.findById(map.getIndustryIndex().getIndexId())
                                )
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(optIndex -> {
                                    if (optIndex.isEmpty()) {
                                        log.warn("[IndustryIndexService] index master missing. industryId={}, indexId={}",
                                                industryId, map.getIndustryIndex().getIndexId());
                                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                                        return Mono.empty();
                                    }

                                    return industryIndexReader.read(
                                            optIndex.get(),
                                            meta,
                                            freq,
                                            window
                                    );
                                })
                )
                .onErrorResume(ex -> {
                    log.warn("[IndustryIndexService] unexpected error. industryId={}, cause={}",
                            industryId, ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                    return Mono.empty();
                });
    }
}