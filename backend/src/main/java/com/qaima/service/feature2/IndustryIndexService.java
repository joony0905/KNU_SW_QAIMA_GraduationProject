package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.domain.IndustryIndexMap;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.industry.RelativePointDto;
import com.qaima.repository.IndustryIndexMapRepository;
import com.qaima.repository.IndustryIndexOhlcvRepository;
import com.qaima.repository.IndustryIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndustryIndexService {

    private final IndustryIndexMapRepository indexMapRepository;
    private final IndustryIndexRepository indexRepository;
    private final IndustryIndexOhlcvRepository ohlcvRepository;

    /**
     * Feature2 – Industry Index loader
     *
     * 정책:
     * - throw 금지
     * - 실패 시 Mono.empty + warning 누적
     * - 산업지수는 1개만 사용
     */
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

        // 1️⃣ industry_id → industry_index_map
        return Mono.fromCallable(() ->
                        indexMapRepository.findFirstByIdIndustryId(industryId)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optMap -> {
                    if (optMap.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                        return Mono.empty();
                    }
                    return Mono.just(optMap.get());
                })

                // 2️⃣ index 메타 조회
                .flatMap(map ->
                        Mono.fromCallable(() ->
                                        indexRepository.findById(
                                                map.getIndustryIndex().getIndexId()
                                        )
                                )
                                .subscribeOn(Schedulers.boundedElastic())
                                .flatMap(optIndex -> {
                                    if (optIndex.isEmpty()) {
                                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                                        return Mono.empty();
                                    }
                                    return loadOhlcvAndBuild(optIndex.get(), meta, freq, window);
                                })
                )

                .onErrorResume(ex -> {
                    log.warn("[IndustryIndexService] unexpected error. industryId={}, cause={}",
                            industryId, ex.getMessage());
                    meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                    return Mono.empty();
                });
    }

    /**
     * 3️⃣ index OHLCV 조회 + 상대% 리베이스
     */
    private Mono<IndustryIndexBlockDto> loadOhlcvAndBuild(
            IndustryIndex index,
            Feature2MetaDto meta,
            Freq freq,
            int window
    ) {
        Pageable pageable = PageRequest.of(
                0,
                window,
                Sort.by(Sort.Direction.DESC, "id.ts")
        );

        return Mono.fromCallable(() ->
                        ohlcvRepository.findRecent(
                                index.getIndexId(),
                                freq,
                                pageable
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(list -> {
                    if (list == null || list.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    // DESC → ASC (시계열 정렬)
                    List<IndustryIndexOhlcv> asc = list.stream()
                            .sorted((a, b) ->
                                    a.getId().getTs().compareTo(b.getId().getTs())
                            )
                            .toList();

                    List<RelativePointDto> series = rebaseToPct(asc);
                    if (series.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    return Mono.just(
                            IndustryIndexBlockDto.builder()
                                    .indexId(index.getIndexId())
                                    .name(index.getName())
                                    .code(index.getCode())
                                    .currency(index.getCurrency())
                                    .series(series)
                                    .build()
                    );
                });
    }

    /**
     * 기준점 = 첫 close
     */
    private List<RelativePointDto> rebaseToPct(List<IndustryIndexOhlcv> list) {
        BigDecimal base = list.get(0).getClose();
        if (base == null || base.signum() == 0) {
            return List.of();
        }

        return list.stream()
                .map(o -> {
                    if (o.getClose() == null) return null;

                    double pct = o.getClose()
                            .subtract(base)
                            .divide(base, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();

                    return RelativePointDto.builder()
                            .ts(o.getId().getTs().toLocalDateTime())
                            .pct(pct)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }
}