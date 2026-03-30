package com.qaima.api.OpendartController;

import com.qaima.common.ApiResponse;
import com.qaima.service.opendart.OpenDartCorpCodeSyncService;
import com.qaima.service.opendart.OpenDartDailySyncService;
import com.qaima.service.issuedshares.IssuedSharesSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/opendart")
// OpenDART 수동 동기화용 관리자 엔드포인트
public class OpenDartAdminController {

    private final OpenDartCorpCodeSyncService corpCodeSyncService;
    private final IssuedSharesSyncService issuedSharesSyncService;
    private final OpenDartDailySyncService dailySyncService;

    @PostMapping("/corp-codes/sync")
    // stock master의 dart_corp_code 매핑을 전체 갱신
    public Mono<ApiResponse<OpenDartCorpCodeSyncService.SyncResult>> syncCorpCodes() {
        return corpCodeSyncService.syncAllStockMappings()
                .map(ApiResponse::success);
    }

    @PostMapping("/issued-shares/sync")
    // DART 매핑이 있는 전체 종목의 발행주식수를 일괄 동기화
    public Mono<ApiResponse<IssuedSharesSyncService.DailyBatchResult>> syncIssuedShares() {
        return issuedSharesSyncService.syncDailyAllMappedStocks()
                .map(ApiResponse::success);
    }

    @PostMapping("/issued-shares/sync/stock")
    // 특정 종목만 단건으로 발행주식수 동기화
    public Mono<ApiResponse<IssuedSharesSyncService.StockSyncResult>> syncIssuedSharesForStock(
            @RequestParam String stockCode
    ) {
        return issuedSharesSyncService.syncDailyForStockCode(stockCode)
                .map(ApiResponse::success);
    }

    @PostMapping("/sync/daily")
    // corp_code 매핑과 발행주식수 일일 동기화를 한 번에 실행
    public Mono<ApiResponse<OpenDartDailySyncService.DailySyncResult>> runDailySync() {
        return dailySyncService.runDailySync()
                .map(ApiResponse::success);
    }
}
