package com.qaima.service.batch;

import com.qaima.domain.Freq;
import com.qaima.domain.IndustryIndex;
import com.qaima.domain.IndustryIndexOhlcv;
import com.qaima.external.IndustryIndexFetcher;
import com.qaima.repository.IndustryIndexOhlcvRepository;
import com.qaima.repository.IndustryIndexRepository;
import com.qaima.service.feature2.IndustryIndexBar;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryIndexOhlcvSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KisBatchProperties properties;
    private final IndustryIndexRepository indexRepository;
    private final IndustryIndexOhlcvRepository ohlcvRepository;
    private final IndustryIndexFetcher industryIndexFetcher;

    public BatchSummary syncDaily() {
        KisBatchProperties.IndustryIndexOhlcv config = properties.getIndustryIndexOhlcv();
        List<IndustryIndex> targets = indexRepository.findAll().stream()
                .sorted(Comparator.comparing(IndustryIndex::getCode))
                .limit(config.getLimit() > 0 ? config.getLimit() : Long.MAX_VALUE)
                .toList();
        LocalDate today = LocalDate.now(KST);
        BatchCounter counter = new BatchCounter();
        List<String> failed = new ArrayList<>();

        for (IndustryIndex index : targets) {
            try {
                FetchRange range = decideFetchRange(index, today, config.getLookbackDays(), config.getRefreshTailDays());
                if (range.skip()) {
                    counter.skipped++;
                    continue;
                }

                List<IndustryIndexBar> fetched = industryIndexFetcher.fetch(
                        index.getCode(),
                        Freq.ONE_D,
                        range.from(),
                        range.to()
                ).block();

                if (fetched == null || fetched.isEmpty()) {
                    counter.empty++;
                    log.warn("[IndustryIndexOhlcvBatch] empty. indexCode={}, from={}, to={}",
                            index.getCode(), range.from(), range.to());
                    continue;
                }

                List<IndustryIndexOhlcv> entities = fetched.stream()
                        .map(bar -> toEntityOrNull(index, bar))
                        .filter(java.util.Objects::nonNull)
                        .toList();

                if (entities.isEmpty()) {
                    counter.empty++;
                    continue;
                }

                ohlcvRepository.saveAll(entities);
                counter.success++;
                counter.saved += entities.size();
            } catch (Exception e) {
                counter.failed++;
                failed.add(index.getCode());
                log.warn("[IndustryIndexOhlcvBatch] failed. indexCode={}, cause={}",
                        index.getCode(), e.getMessage(), e);
            }
        }

        BatchSummary summary = new BatchSummary(
                targets.size(),
                counter.success,
                counter.skipped,
                counter.empty,
                0,
                counter.failed,
                0,
                0,
                counter.saved,
                failed
        );
        log.info("[IndustryIndexOhlcvBatch] finished. {}", summary.toLogString());
        return summary;
    }

    private FetchRange decideFetchRange(IndustryIndex index, LocalDate today, int lookbackDays, int refreshTailDays) {
        List<IndustryIndexOhlcv> latestRows = ohlcvRepository.findRecent(
                index.getIndexId(),
                Freq.ONE_D,
                PageRequest.of(0, 1)
        );
        if (latestRows.isEmpty()) {
            return new FetchRange(today.minusDays(Math.max(1, lookbackDays)), today, false);
        }

        OffsetDateTime latestTs = latestRows.get(0).getId().getTs();
        LocalDate latestDate = latestTs.toLocalDate();
        if (!latestDate.isBefore(today)) {
            return new FetchRange(latestDate, today, true);
        }

        LocalDate from = latestDate.minusDays(2);
        LocalDate tail = today.minusDays(Math.max(1, refreshTailDays));
        if (tail.isAfter(from)) {
            from = tail;
        }
        return new FetchRange(from, today, false);
    }

    private IndustryIndexOhlcv toEntityOrNull(IndustryIndex index, IndustryIndexBar bar) {
        try {
            return bar.toEntity(index, Freq.ONE_D);
        } catch (Exception e) {
            log.debug("[IndustryIndexOhlcvBatch] invalid bar skipped. indexCode={}, cause={}",
                    index.getCode(), e.getMessage());
            return null;
        }
    }

    private record FetchRange(LocalDate from, LocalDate to, boolean skip) {
    }

    private static class BatchCounter {
        int success;
        int skipped;
        int empty;
        int failed;
        int saved;
    }
}
