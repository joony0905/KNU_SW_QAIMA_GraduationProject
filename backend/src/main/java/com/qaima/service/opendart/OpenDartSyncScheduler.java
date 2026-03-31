package com.qaima.service.opendart;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenDartSyncScheduler {

    private final OpenDartDailySyncService dailySyncService;

    @Value("${opendart.sync.enabled:true}")
    private boolean syncEnabled;

    @Scheduled(cron = "${opendart.sync.daily-cron:0 10 3 * * *}", zone = "Asia/Seoul")
    public void runDailySync() {
        if (!syncEnabled) {
            log.info("[OpenDART] daily sync skipped because opendart.sync.enabled=false");
            return;
        }

        try {
            OpenDartDailySyncService.DailySyncResult result = dailySyncService.runDailySync().block();
            if (result != null) {
                log.info("[OpenDART] scheduled daily sync finished. corpUpdated={}, issuedCreated={}, issuedUpdated={}, issuedUnchanged={}",
                        result.corpCodeSyncResult().updatedStocks(),
                        result.issuedSharesSyncResult().createdRows(),
                        result.issuedSharesSyncResult().updatedRows(),
                        result.issuedSharesSyncResult().unchangedRows());
            }
        } catch (Exception e) {
            log.error("[OpenDART] scheduled daily sync failed", e);
        }
    }
}
