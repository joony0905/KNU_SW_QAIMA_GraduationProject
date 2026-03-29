package com.qaima.service.marketdata.reader;

import com.qaima.common.Feat2WarningCode;
import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.dto.feature2.Feature2MetaDto;
import com.qaima.dto.industry.IndustryIndexBlockDto;
import com.qaima.dto.peercluster.RelativePointDto;
import com.qaima.external.IndustryIndexFetcher;
import com.qaima.repository.IndustryIndexOhlcvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndustryIndexReaderImpl implements IndustryIndexReader {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration TTL = Duration.ofMinutes(30);

    private final IndustryIndexOhlcvRepository ohlcvRepository;
    private final IndustryIndexFetcher industryIndexFetcher;
    private final ReactiveRedisTemplate<String, IndustryIndexBlockDto> redisTemplate;

    /**
     * 동일 key에 대한 중복 로딩 방지
     */
    private final ConcurrentHashMap<String, Mono<IndustryIndexBlockDto>> inflight = new ConcurrentHashMap<>();

    @Override
    public Mono<IndustryIndexBlockDto> read(
            IndustryIndex index,
            Feature2MetaDto meta,
            Freq freq,
            int window
    ) {
        if (index == null || index.getIndexId() == null || index.getCode() == null || freq == null || window <= 0) {
            return Mono.empty();
        }

        String cacheKey = buildKey(index.getCode(), freq, window);

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .onErrorResume(e -> {
                    log.warn("[IndustryIndexReader] cache read failed. fallback to DB/fetch. key={}", cacheKey, e);
                    return Mono.empty();
                })
                .flatMap(cached -> {
                    log.info("[IndustryIndexReader] cache hit key={}", cacheKey);
                    return Mono.just(cached);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("[IndustryIndexReader] cache miss key={}", cacheKey);

                    return inflight.computeIfAbsent(cacheKey, k ->
                            loadFromDbOrFetch(index, meta, freq, window)
                                    .flatMap(block ->
                                            redisTemplate.opsForValue()
                                                    .set(k, block, TTL)
                                                    .doOnNext(saved -> log.debug(
                                                            "[IndustryIndexReader] cache set key={}, saved={}",
                                                            k, saved
                                                    ))
                                                    .onErrorResume(e -> {
                                                        log.warn("[IndustryIndexReader] cache write failed. continue without cache. key={}", k, e);
                                                        return Mono.just(false);
                                                    })
                                                    .thenReturn(block)
                                    )
                                    .cache()
                                    .doFinally(sig -> {
                                        inflight.remove(k);
                                        log.debug("[IndustryIndexReader] inflight cleared key={}", k);
                                    })
                    );
                }));
    }

    private Mono<IndustryIndexBlockDto> loadFromDbOrFetch(
            IndustryIndex index,
            Feature2MetaDto meta,
            Freq freq,
            int window
    ) {
        return Mono.fromCallable(() ->
                        ohlcvRepository.findRecent(
                                index.getIndexId(),
                                freq,
                                PageRequest.of(0, window, Sort.by(Sort.Direction.DESC, "id.ts"))
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(rows -> {
                    int size = rows == null ? 0 : rows.size();
                    log.info("[IndustryIndexReader] db recent lookup. indexCode={}, indexId={}, freq={}, window={}, rows={}",
                            index.getCode(), index.getIndexId(), freq, window, size);

                    if (rows != null && rows.size() >= window) {
                        return buildBlock(index, rows, meta);
                    }

                    log.info("[IndustryIndexReader] db rows insufficient. indexCode={}, indexId={}, freq={}, window={}, rows={}",
                            index.getCode(), index.getIndexId(), freq, window, size);

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

        log.info("[IndustryIndexReader] fetch start. indexCode={}, indexId={}, freq={}, window={}, from={}, to={}",
                index.getCode(), index.getIndexId(), freq, window, from, to);

        return industryIndexFetcher.fetch(index.getCode(), freq, from, to)
                .flatMap(fetched -> {
                    int fetchedSize = fetched == null ? 0 : fetched.size();
                    log.info("[IndustryIndexReader] fetch done. indexCode={}, fetchedRows={}",
                            index.getCode(), fetchedSize);

                    if (fetched == null || fetched.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_FETCH_FAILED);
                        return Mono.empty();
                    }

                    List<IndustryIndexOhlcv> entities = fetched.stream()
                            .map(item -> item.toEntity(index, freq))
                            .filter(Objects::nonNull)
                            .toList();

                    log.info("[IndustryIndexReader] entity mapped. indexCode={}, entityRows={}",
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

                    log.info("[IndustryIndexReader] entity sanitized. indexCode={}, sanitizedRows={}",
                            index.getCode(), sanitized.size());

                    if (sanitized.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    List<IndustryIndexOhlcv> limited = sanitized.stream()
                            .skip(Math.max(0, sanitized.size() - window))
                            .toList();

                    log.info("[IndustryIndexReader] entity limited. indexCode={}, limitedRows={}",
                            index.getCode(), limited.size());

                    if (limited.isEmpty()) {
                        meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_OHLCV_EMPTY);
                        return Mono.empty();
                    }

                    Mono<List<IndustryIndexOhlcv>> saveMono = Mono.fromCallable(() -> ohlcvRepository.saveAll(entities))
                            .subscribeOn(Schedulers.boundedElastic())
                            .doOnNext(saved -> log.info(
                                    "[IndustryIndexReader] save success. indexCode={}, savedRows={}",
                                    index.getCode(),
                                    saved == null ? 0 : saved.size()
                            ))
                            .onErrorResume(ex -> {
                                log.warn("[IndustryIndexReader] save failed. indexCode={}, cause={}",
                                        index.getCode(), ex.getMessage(), ex);
                                meta.addWarning(Feat2WarningCode.INDUSTRY_INDEX_SAVE_FAILED);
                                return Mono.just(List.of());
                            });

                    return saveMono.then(buildBlock(index, limited, meta));
                })
                .onErrorResume(ex -> {
                    log.warn("[IndustryIndexReader] fetch failed. indexCode={}, cause={}",
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

        log.info("[IndustryIndexReader] buildBlock input. indexCode={}, rawRows={}, ascRows={}",
                index.getCode(),
                raw == null ? 0 : raw.size(),
                asc.size());

        List<RelativePointDto> series = rebaseToPct(asc);

        log.info("[IndustryIndexReader] buildBlock series. indexCode={}, seriesRows={}",
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
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        BigDecimal base = list.get(0).getClose();
        if (base == null || base.signum() == 0) {
            return List.of();
        }

        return list.stream()
                .map(o -> {
                    if (o.getClose() == null) {
                        return null;
                    }

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

    private String buildKey(String indexCode, Freq freq, int window) {
        return "industry:index:" + indexCode + ":" + freq.name() + ":" + window;
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