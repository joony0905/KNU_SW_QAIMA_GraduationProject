package com.qaima.service.opendart;

import com.qaima.service.issuedshares.IssuedSharesSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenDartDailySyncService {

    private final OpenDartCorpCodeSyncService corpCodeSyncService;
    private final IssuedSharesSyncService issuedSharesSyncService;

    public Mono<DailySyncResult> runDailySync() {
        return corpCodeSyncService.syncAllStockMappings()
                .flatMap(mappingResult -> issuedSharesSyncService.syncDailyAllMappedStocks()
                        .map(issuedSharesResult -> new DailySyncResult(mappingResult, issuedSharesResult)))
                .doOnNext(result -> log.info("[OpenDART] daily sync complete. corpUpdated={}, issuedCreated={}, issuedUpdated={}, issuedUnchanged={}",
                        result.corpCodeSyncResult().updatedStocks(),
                        result.issuedSharesSyncResult().createdRows(),
                        result.issuedSharesSyncResult().updatedRows(),
                        result.issuedSharesSyncResult().unchangedRows()));
    }

    public record DailySyncResult(
            OpenDartCorpCodeSyncService.SyncResult corpCodeSyncResult,
            IssuedSharesSyncService.DailyBatchResult issuedSharesSyncResult
    ) {
    }
}
