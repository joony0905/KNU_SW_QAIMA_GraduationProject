package com.qaima.api.SecAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.service.issuedshares.SecIssuedSharesSyncService;
import com.qaima.service.sec.Sec13fInstitutionalHoldingImportService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/sec")
public class SecAdminController {

    private final SecIssuedSharesSyncService issuedSharesSyncService;
    private final Sec13fInstitutionalHoldingImportService sec13fInstitutionalHoldingImportService;

    @PostMapping("/issued-shares/sync")
    public Mono<ApiResponse<SecIssuedSharesSyncService.BatchResult>> syncIssuedShares(
            @RequestParam(defaultValue = "300") int limit
    ) {
        return issuedSharesSyncService.syncAllMappedStocks(limit)
                .map(ApiResponse::success);
    }

    @PostMapping("/issued-shares/sync/stock")
    public Mono<ApiResponse<SecIssuedSharesSyncService.StockSyncResult>> syncIssuedSharesForStock(
            @RequestParam String stockCode
    ) {
        return issuedSharesSyncService.syncForStockCode(stockCode)
                .map(ApiResponse::success);
    }

    @PostMapping("/13f/import/file")
    public Mono<ApiResponse<Sec13fInstitutionalHoldingImportService.ImportFileResult>> importSec13fFile(
            @RequestParam String filePath,
            @RequestParam(defaultValue = "false") boolean aggregate
    ) {
        return sec13fInstitutionalHoldingImportService.importFile(filePath, aggregate)
                .map(ApiResponse::success);
    }

    @PostMapping("/13f/import/source")
    public Mono<ApiResponse<Sec13fInstitutionalHoldingImportService.ImportDirectoryResult>> importSec13fSource(
            @RequestParam(required = false) String sourceDir,
            @RequestParam(defaultValue = "0") int limit,
            @RequestParam(defaultValue = "false") boolean aggregate
    ) {
        return sec13fInstitutionalHoldingImportService.importDirectory(sourceDir, limit, aggregate)
                .map(ApiResponse::success);
    }

    @PostMapping("/13f/aggregates/rebuild")
    public Mono<ApiResponse<Sec13fInstitutionalHoldingImportService.AggregateRebuildResult>> rebuildSec13fAggregates(
            @RequestParam(required = false) Integer stockLimit
    ) {
        return sec13fInstitutionalHoldingImportService.rebuildAggregates(stockLimit)
                .map(ApiResponse::success);
    }

    @GetMapping("/13f/holdings/stock")
    public Mono<ApiResponse<List<Sec13fInstitutionalHoldingImportService.QuarterlyHoldingResult>>> findSec13fHoldings(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "12") int limit
    ) {
        return sec13fInstitutionalHoldingImportService.findQuarterlyHoldings(stockCode, limit)
                .map(ApiResponse::success);
    }

    @PostMapping("/13f/mappings/cusip")
    public Mono<ApiResponse<Sec13fInstitutionalHoldingImportService.CusipMappingResult>> upsertCusipMapping(
            @RequestParam String stockCode,
            @RequestParam String cusip,
            @RequestParam(required = false) String issuerName,
            @RequestParam(required = false) Integer confidence
    ) {
        return sec13fInstitutionalHoldingImportService.upsertCusipMapping(stockCode, cusip, issuerName, confidence)
                .map(ApiResponse::success);
    }
}
