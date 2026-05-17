package com.qaima.service.issuedshares;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecIssuedSharesSyncScheduler {

    private final SecIssuedSharesSyncService syncService;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Value("${sec.issued-shares.sync.enabled:true}")
    private boolean syncEnabled;

    @Value("${sec.issued-shares.sync.limit:300}")
    private int syncLimit;

    @Scheduled(cron = "${sec.issued-shares.sync.daily-cron:0 0 18 * * *}", zone = "Asia/Seoul")
    public void runDailySync() {
        if (!syncEnabled) {
            log.info("[SEC] issued shares scheduled sync skipped because sec.issued-shares.sync.enabled=false");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.info("[SEC] issued shares scheduled sync skipped because previous run is still active");
            return;
        }

        try {
            SecIssuedSharesSyncService.BatchResult result = syncService.syncAllMappedStocks(syncLimit).block();
            if (result != null) {
                log.info("[SEC] issued shares scheduled sync finished. availableStocks={}, targetStocks={}, processedStocks={}, noDataStocks={}, failedStocks={}, createdRows={}, updatedRows={}, unchangedRows={}",
                        result.availableStocks(),
                        result.targetStocks(),
                        result.processedStocks(),
                        result.noDataStocks(),
                        result.failedStocks(),
                        result.createdRows(),
                        result.updatedRows(),
                        result.unchangedRows());
            }
        } catch (Exception e) {
            log.error("[SEC] issued shares scheduled sync failed", e);
        } finally {
            running.set(false);
        }
    }
}
