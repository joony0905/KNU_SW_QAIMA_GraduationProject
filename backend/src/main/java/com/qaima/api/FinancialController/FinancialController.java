package com.qaima.api.FinancialController;

import com.qaima.common.ApiResponse;
import com.qaima.domain.PeriodType;
import com.qaima.dto.financial.FinancialDto;
import com.qaima.service.financial.FinancialReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stocks")
public class FinancialController {

    private final FinancialReadService financialQueryService;

    /**
     * 기본: 연간(A) 5개년
     * 예: GET /api/v1/stocks/005930/financials?years=5
     *
     * 확장:
     * - 분기 전체: periodType=Q
     * - 특정 분기만: periodType=Q&periodNo=3
     * - 반기 전체: periodType=H
     * - 하반기만: periodType=H&periodNo=2
     * - TTM: periodType=TTM (periodNo는 생략하거나 0)
    */

    @GetMapping("/{ticker}/financials")
    public Mono<ApiResponse<List<FinancialDto>>> getFinancialsForLastNYears(
            @PathVariable String ticker,
            @RequestParam(name = "years", defaultValue = "5") int years,
            @RequestParam(name = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(name = "periodType", required = false) PeriodType periodType,
            @RequestParam(name = "periodNo", required = false) Integer periodNo
    ) {
        PeriodType pt = (periodType != null ? periodType : PeriodType.A);
        return financialQueryService.getForLastNYears(ticker, pt, periodNo, years, asOfDate)
                .map(ApiResponse::success);
    }

    /**
     * 기본: 연간(A) 단일 연도
     * 예: GET /api/v1/stocks/005930/financials/2023
     *
     * 확장:
     * 예: GET /api/v1/stocks/005930/financials/2023?periodType=H&periodNo=2
     */

    @GetMapping("/{ticker}/financials/{year}")
    public Mono<ApiResponse<List<FinancialDto>>> getFinancialsForYear(
            @PathVariable String ticker,
            @PathVariable int year,
            @RequestParam(name = "periodType", required = false) PeriodType periodType,
            @RequestParam(name = "periodNo", required = false) Integer periodNo
    ) {
        PeriodType pt = (periodType != null ? periodType : PeriodType.A);
        return financialQueryService.getForYear(ticker, pt, periodNo, year)
                .map(ApiResponse::success);
    }
}

