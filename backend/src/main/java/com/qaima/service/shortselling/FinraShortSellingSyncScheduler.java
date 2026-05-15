package com.qaima.service.shortselling;

import com.qaima.service.shortselling.FinraShortSellingSyncService.FinraShortSellingBackfillResult;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinraShortSellingSyncScheduler {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private final FinraShortSellingSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${finra.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${finra.sync.lookback-days:7}")
    private int lookbackDays;

    @Scheduled(cron = "${finra.sync.daily-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void runDailyLookbackSync() {
        if (!syncEnabled) {
            log.info("[FINRA] scheduled sync skipped because finra.sync.enabled=false");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("[FINRA] scheduled sync skipped because previous sync is still running");
            return;
        }

        int days = Math.max(1, lookbackDays);
        LocalDate to = LocalDate.now(NEW_YORK);
        LocalDate from = to.minusDays(days - 1L);

        try {
            FinraShortSellingBackfillResult result = syncService.backfill(from, to).block();
            if (result != null) {
                log.info("[FINRA] scheduled lookback sync finished. from={}, to={}, requestedDays={}, dataDays={}, fetched={}, upserted={}, unknown={}, ambiguous={}",
                        result.requestedFrom(),
                        result.requestedTo(),
                        result.requestedDays(),
                        result.dataDays(),
                        result.fetchedCount(),
                        result.upsertedCount(),
                        result.skippedUnknownStockCount(),
                        result.skippedAmbiguousStockCount());
            }
        } catch (Exception e) {
            log.error("[FINRA] scheduled lookback sync failed. from={}, to={}, lookbackDays={}", from, to, days, e);
        } finally {
            running.set(false);
        }
    }
}
