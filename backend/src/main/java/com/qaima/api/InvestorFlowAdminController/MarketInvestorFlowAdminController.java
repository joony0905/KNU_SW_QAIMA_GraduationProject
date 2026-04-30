package com.qaima.api.InvestorFlowAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.MarketInvestorFlow;
import com.qaima.external.KisInvestorFlowClient;
import com.qaima.service.investorflow.MarketInvestorFlowService;
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
@RequestMapping("/api/v1/admin/investor-flow/market")
public class MarketInvestorFlowAdminController {

    private final MarketInvestorFlowService marketInvestorFlowService;

    // 시장 단위 투자자 수급을 기간 적재한다. marketCode는 KSP(코스피), KSQ(코스닥)를 사용한다.
    // curl -X POST "http://localhost:8080/api/v1/admin/investor-flow/market/sync?marketCode=KSP&industryCode=0000&from=2026-04-01&to=2026-04-30"
    // curl -X POST "http://localhost:8080/api/v1/admin/investor-flow/market/sync?marketCode=KSQ&industryCode=0000&from=2026-04-01&to=2026-04-30"
    @PostMapping("/sync")
    public Mono<ApiResponse<MarketInvestorFlowSyncResult>> sync(
            @RequestParam(defaultValue = "KSP") String marketCode,
            @RequestParam(defaultValue = KisInvestorFlowClient.DEFAULT_MARKET_INDUSTRY_CODE) String industryCode,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return marketInvestorFlowService.sync(marketCode, industryCode, from, to)
                .map(rows -> MarketInvestorFlowSyncResult.from(marketCode, industryCode, rows))
                .map(ApiResponse::success);
    }

    @GetMapping("/series")
    public Mono<ApiResponse<List<MarketInvestorFlowRow>>> series(
            @RequestParam(defaultValue = "KSP") String marketCode,
            @RequestParam(defaultValue = KisInvestorFlowClient.DEFAULT_MARKET_INDUSTRY_CODE) String industryCode,
            @RequestParam(defaultValue = "30") int limit
    ) {
        return marketInvestorFlowService.latestRows(marketCode, industryCode, limit)
                .map(rows -> rows.stream().map(MarketInvestorFlowRow::from).toList())
                .map(ApiResponse::success);
    }

    public record MarketInvestorFlowSyncResult(
            String requestedMarketCode,
            String requestedIndustryCode,
            int savedCount,
            LocalDate firstTradeDate,
            LocalDate lastTradeDate,
            List<MarketInvestorFlowRow> rows
    ) {
        static MarketInvestorFlowSyncResult from(String marketCode, String industryCode, List<MarketInvestorFlow> rows) {
            List<MarketInvestorFlow> safeRows = rows == null ? List.of() : rows;
            LocalDate first = safeRows.stream()
                    .map(MarketInvestorFlow::getTradeDate)
                    .min(LocalDate::compareTo)
                    .orElse(null);
            LocalDate last = safeRows.stream()
                    .map(MarketInvestorFlow::getTradeDate)
                    .max(LocalDate::compareTo)
                    .orElse(null);
            return new MarketInvestorFlowSyncResult(
                    marketCode,
                    industryCode,
                    safeRows.size(),
                    first,
                    last,
                    safeRows.stream().map(MarketInvestorFlowRow::from).toList()
            );
        }
    }

    public record MarketInvestorFlowRow(
            String marketCode,
            String industryCode,
            LocalDate tradeDate,
            BigDecimal foreignNetBuyQty,
            BigDecimal foreignNetBuyValueMillion,
            BigDecimal individualNetBuyQty,
            BigDecimal individualNetBuyValueMillion,
            BigDecimal institutionNetBuyQty,
            BigDecimal institutionNetBuyValueMillion,
            String source,
            String sourceTrId
    ) {
        static MarketInvestorFlowRow from(MarketInvestorFlow row) {
            return new MarketInvestorFlowRow(
                    row.getMarketCode(),
                    row.getIndustryCode(),
                    row.getTradeDate(),
                    row.getForeignNetBuyQty(),
                    row.getForeignNetBuyValueMillion(),
                    row.getIndividualNetBuyQty(),
                    row.getIndividualNetBuyValueMillion(),
                    row.getInstitutionNetBuyQty(),
                    row.getInstitutionNetBuyValueMillion(),
                    row.getSource(),
                    row.getSourceTrId()
            );
        }
    }
}
