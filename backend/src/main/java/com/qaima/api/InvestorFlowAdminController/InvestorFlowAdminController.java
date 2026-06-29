package com.qaima.api.InvestorFlowAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.StockInvestorFlow;
import com.qaima.service.investorflow.StockInvestorFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/investor-flow/stock")
@Tag(name = "Admin - Market Data", description = "Admin-only endpoints. Requires bearer token with ADMIN role.")
@SecurityRequirement(name = "bearerAuth")
public class InvestorFlowAdminController {

    private final StockInvestorFlowService stockInvestorFlowService;

    // 지정 종목의 최근 투자자 수급을 적재한다. KIS 제한상 당일 데이터는 보통 15:40 이후 조회 가능하다.
    // curl -X POST "http://localhost:8080/api/v1/admin/investor-flow/stock/sync/latest?stockCode=005930"
    // curl -X POST "http://localhost:8080/api/v1/admin/investor-flow/stock/sync/latest?stockCode=005930&asOfDate=2026-04-29"
    @PostMapping("/sync/latest")
    @Operation(summary = "Sync latest stock investor flow")
    public Mono<ApiResponse<StockInvestorFlowSyncResult>> syncLatest(
            @RequestParam String stockCode,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return stockInvestorFlowService.syncLatest(stockCode, asOfDate)
                .map(rows -> StockInvestorFlowSyncResult.from(stockCode, rows))
                .map(ApiResponse::success);
    }

    // 지정 종목의 투자자 수급을 기간 적재한다. 초기/대량 적재는 batch/jobs/load_stock_investor_flow.py 사용을 권장한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/investor-flow/stock/backfill?stockCode=005930&from=2026-04-01&to=2026-04-30"
    @PostMapping("/backfill")
    @Operation(summary = "Backfill stock investor flow")
    public Mono<ApiResponse<StockInvestorFlowSyncResult>> backfill(
            @RequestParam String stockCode,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return stockInvestorFlowService.backfill(stockCode, from, to)
                .map(rows -> StockInvestorFlowSyncResult.from(stockCode, rows))
                .map(ApiResponse::success);
    }

    @GetMapping("/series")
    @Operation(summary = "List stock investor flow series")
    public Mono<ApiResponse<List<StockInvestorFlowRow>>> series(
            @RequestParam String stockCode,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return stockInvestorFlowService.latestRows(stockCode, limit)
                .map(rows -> rows.stream().map(StockInvestorFlowRow::from).toList())
                .map(ApiResponse::success);
    }

    public record StockInvestorFlowSyncResult(
            String requestedStockCode,
            int savedCount,
            LocalDate firstTradeDate,
            LocalDate lastTradeDate,
            List<StockInvestorFlowRow> rows
    ) {
        static StockInvestorFlowSyncResult from(String requestedStockCode, List<StockInvestorFlow> rows) {
            List<StockInvestorFlow> safeRows = rows == null ? List.of() : rows;
            LocalDate first = safeRows.stream()
                    .map(StockInvestorFlow::getTradeDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate last = safeRows.stream()
                    .map(StockInvestorFlow::getTradeDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            return new StockInvestorFlowSyncResult(
                    requestedStockCode,
                    safeRows.size(),
                    first,
                    last,
                    safeRows.stream().map(StockInvestorFlowRow::from).toList()
            );
        }
    }

    public record StockInvestorFlowRow(
            String stockCode,
            LocalDate tradeDate,
            String marketDivCode,
            BigDecimal closePrice,
            BigDecimal accumulatedVolume,
            BigDecimal accumulatedTradingValueMillion,
            BigDecimal foreignNetBuyQty,
            BigDecimal foreignNetBuyValueMillion,
            BigDecimal individualNetBuyQty,
            BigDecimal individualNetBuyValueMillion,
            BigDecimal institutionNetBuyQty,
            BigDecimal institutionNetBuyValueMillion,
            BigDecimal securitiesNetBuyQty,
            BigDecimal securitiesNetBuyValueMillion,
            BigDecimal investmentTrustNetBuyQty,
            BigDecimal investmentTrustNetBuyValueMillion,
            BigDecimal privateFundNetBuyQty,
            BigDecimal privateFundNetBuyValueMillion,
            BigDecimal bankNetBuyQty,
            BigDecimal bankNetBuyValueMillion,
            BigDecimal insuranceNetBuyQty,
            BigDecimal insuranceNetBuyValueMillion,
            BigDecimal fundNetBuyQty,
            BigDecimal fundNetBuyValueMillion,
            BigDecimal otherNetBuyQty,
            BigDecimal otherNetBuyValueMillion,
            String source,
            String sourceTrId
    ) {
        static StockInvestorFlowRow from(StockInvestorFlow row) {
            return new StockInvestorFlowRow(
                    row.getStockCode(),
                    row.getTradeDate(),
                    row.getMarketDivCode(),
                    row.getClosePrice(),
                    row.getAccumulatedVolume(),
                    row.getAccumulatedTradingValueMillion(),
                    row.getForeignNetBuyQty(),
                    row.getForeignNetBuyValueMillion(),
                    row.getIndividualNetBuyQty(),
                    row.getIndividualNetBuyValueMillion(),
                    row.getInstitutionNetBuyQty(),
                    row.getInstitutionNetBuyValueMillion(),
                    row.getSecuritiesNetBuyQty(),
                    row.getSecuritiesNetBuyValueMillion(),
                    row.getInvestmentTrustNetBuyQty(),
                    row.getInvestmentTrustNetBuyValueMillion(),
                    row.getPrivateFundNetBuyQty(),
                    row.getPrivateFundNetBuyValueMillion(),
                    row.getBankNetBuyQty(),
                    row.getBankNetBuyValueMillion(),
                    row.getInsuranceNetBuyQty(),
                    row.getInsuranceNetBuyValueMillion(),
                    row.getFundNetBuyQty(),
                    row.getFundNetBuyValueMillion(),
                    row.getOtherNetBuyQty(),
                    row.getOtherNetBuyValueMillion(),
                    row.getSource(),
                    row.getSourceTrId()
            );
        }
    }
}
