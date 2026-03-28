package com.qaima.service.feature2;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.peercluster.RelativePointDto;
import com.qaima.external.IndustryIndexFetcher;
import com.qaima.repository.IndustryIndexMapRepository;
import com.qaima.repository.IndustryIndexOhlcvRepository;
import com.qaima.repository.IndustryIndexRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndustryIndexService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final IndustryIndexMapRepository indexMapRepository;
    private final IndustryIndexRepository indexRepository;
    private final IndustryIndexOhlcvRepository ohlcvRepository;
    private final IndustryIndexFetcher industryIndexFetcher;

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
                                    return loadOrFetchAndBuild(optIndex.get(), meta, freq, window);
                                })
                )
                .onErrorResume(ex -> {
                    log.warn("[IndustryIndexService] unexpected error. industryId={}, cause={}",
                            industryId, ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_MISSING);
                    return Mono.empty();
                });
    }

    private Mono<IndustryIndexBlockDto> loadOrFetchAndBuild(
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
                        ohlcvRepository.findRecent(index.getIndexId(), freq, pageable)
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(list -> {
                    int dbSize = list == null ? 0 : list.size();
                    log.info("[IndustryIndexService] db recent lookup. indexCode={}, indexId={}, freq={}, window={}, rows={}",
                            index.getCode(), index.getIndexId(), freq, window, dbSize);

                    if (list != null && !list.isEmpty()) {
                        return buildBlock(index, list, meta);
                    }

                    return fetchFromKisAndMaybeSave(index, meta, freq, window);
                });
    }

    private Mono<IndustryIndexBlockDto> fetchFromKisAndMaybeSave(
            IndustryIndex index,
            Feature2MetaDto meta,
            Freq freq,
            int window
    ) {
        LocalDate to = LocalDate.now(KST);
        LocalDate from = estimateFromDate(freq, window, to);

        log.info("[IndustryIndexService] fetch start. indexCode={}, indexId={}, freq={}, window={}, from={}, to={}",
                index.getCode(), index.getIndexId(), freq, window, from, to);

        return industryIndexFetcher.fetch(index.getCode(), freq, from, to)
                .flatMap(fetched -> {
                    int fetchedSize = fetched == null ? 0 : fetched.size();
                    log.info("[IndustryIndexService] fetch done. indexCode={}, fetchedRows={}",
                            index.getCode(), fetchedSize);

                    if (fetched == null || fetched.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    List<IndustryIndexOhlcv> entities = fetched.stream()
                            .map(item -> item.toEntity(index, freq))
                            .filter(Objects::nonNull)
                            .toList();

                    log.info("[IndustryIndexService] entity mapped. indexCode={}, entityRows={}",
                            index.getCode(), entities.size());

                    if (entities.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    List<IndustryIndexOhlcv> sanitized = entities.stream()
                            .filter(o -> o.getId() != null && o.getId().getTs() != null)
                            .filter(o -> o.getClose() != null)
                            .sorted(Comparator.comparing(o -> o.getId().getTs()))
                            .toList();

                    log.info("[IndustryIndexService] entity sanitized. indexCode={}, sanitizedRows={}",
                            index.getCode(), sanitized.size());

                    if (sanitized.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    List<IndustryIndexOhlcv> limited = sanitized.stream()
                            .skip(Math.max(0, sanitized.size() - window))
                            .toList();

                    log.info("[IndustryIndexService] entity limited. indexCode={}, limitedRows={}",
                            index.getCode(), limited.size());

                    if (limited.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    Mono<List<IndustryIndexOhlcv>> saveMono =
                            Mono.fromCallable(() -> ohlcvRepository.saveAll(entities))
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .doOnNext(saved -> log.info(
                                            "[IndustryIndexService] save success. indexCode={}, savedRows={}",
                                            index.getCode(),
                                            saved == null ? 0 : saved.size()
                                    ))
                                    .onErrorResume(ex -> {
                                        log.warn("[IndustryIndexService] save failed. indexCode={}, cause={}",
                                                index.getCode(), ex.getMessage(), ex);
                                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_SAVE_FAILED);
                                        return Mono.just(List.of());
                                    });

                    return saveMono.then(buildBlock(index, limited, meta));
                })
                .onErrorResume(ex -> {
                    log.warn("[IndustryIndexService] fetch failed. indexCode={}, cause={}",
                            index.getCode(), ex.getMessage(), ex);
                    meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_FETCH_FAILED);
                    return Mono.empty();
                });
    }

    private Mono<IndustryIndexBlockDto> buildBlock(
            IndustryIndex index,
            List<IndustryIndexOhlcv> raw,
            Feature2MetaDto meta
    ) {
        List<IndustryIndexOhlcv> asc = raw.stream()
                .filter(Objects::nonNull)
                .filter(o -> o.getId() != null && o.getId().getTs() != null)
                .filter(o -> o.getClose() != null)
                .sorted(Comparator.comparing(o -> o.getId().getTs()))
                .toList();

        log.info("[IndustryIndexService] buildBlock input. indexCode={}, rawRows={}, ascRows={}",
                index.getCode(),
                raw == null ? 0 : raw.size(),
                asc.size());

        List<RelativePointDto> series = rebaseToPct(asc);

        log.info("[IndustryIndexService] buildBlock series. indexCode={}, seriesRows={}",
                index.getCode(), series.size());

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
    }

    private List<RelativePointDto> rebaseToPct(List<IndustryIndexOhlcv> list) {
        if (list == null || list.isEmpty()) return List.of();

        BigDecimal base = list.get(0).getClose();
        if (base == null || base.signum() == 0) return List.of();

        return list.stream()
                .map(o -> {
                    if (o.getClose() == null) return null;

                    double pct = o.getClose()
                            .subtract(base)
                            .divide(base, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue();

                    return RelativePointDto.builder()
                            .ts(o.getId().getTs())
                            .pct(pct)
                            .build();
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private LocalDate estimateFromDate(Freq freq, int window, LocalDate to) {
        return switch (freq) {
            case ONE_D -> to.minusDays(Math.max(window * 2L, 180L));
            case ONE_W -> to.minusWeeks(Math.max(window * 2L, 104L));
            case ONE_M -> to.minusMonths(Math.max(window * 2L, 120L));
            default -> to.minusDays(365);
        };
    }
}