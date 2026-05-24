package com.qaima.service.shortselling;

import com.qaima.service.shortselling.KrxShortSellingSyncService.KrxShortSellingBackfillResult;
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
public class KrxShortSellingSyncScheduler {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final KrxShortSellingSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${krx.short-selling.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${krx.short-selling.sync.lookback-days:5}")
    private int lookbackDays;

    @Scheduled(cron = "${krx.short-selling.sync.daily-cron:0 30 18 * * MON-FRI}", zone = "Asia/Seoul")
    public void runDailyLookbackSync() {
        if (!syncEnabled) {
            log.info("[KRX] scheduled short selling sync skipped because krx.short-selling.sync.enabled=false");
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("[KRX] scheduled short selling sync skipped because previous sync is still running");
            return;
        }

        int days = Math.max(1, lookbackDays);
        LocalDate to = LocalDate.now(SEOUL);
        LocalDate from = to.minusDays(days - 1L);

        try {
            KrxShortSellingBackfillResult result = syncService.backfill(from, to).block();
            if (result != null) {
                log.info("[KRX] scheduled short selling lookback sync finished. from={}, to={}, requestedDays={}, dataDays={}, fetched={}, upserted={}, unknown={}",
                        result.requestedFrom(),
                        result.requestedTo(),
                        result.requestedDays(),
                        result.dataDays(),
                        result.fetchedCount(),
                        result.upsertedCount(),
                        result.skippedUnknownStockCount());
            }
        } catch (Exception e) {
            log.error("[KRX] scheduled short selling lookback sync failed. from={}, to={}, lookbackDays={}", from, to, days, e);
        } finally {
            running.set(false);
        }
    }
}
