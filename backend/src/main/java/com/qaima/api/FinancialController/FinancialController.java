package com.qaima.api.FinancialController;

import com.qaima.dto.FinancialDto;
import com.qaima.service.FinancialReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
public class FinancialController {

    private final FinancialReadService financialQueryService;

    /**
     * 예: GET /api/stocks/005930/financials?years=5
     *     GET /api/stocks/AAPL/financials?years=3
     */
    @GetMapping("/{ticker}/financials")
    public List<FinancialDto> getFinancialsForLastNYears(
            @PathVariable String ticker,
            @RequestParam(name = "years", defaultValue = "5") int years,
            @RequestParam(name = "asOfDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return financialQueryService.getAnnualForLastNYears(ticker, years, asOfDate);
    }

    /**
     * 예: GET /api/stocks/005930/financials/2023
     */
    @GetMapping("/{ticker}/financials/{year}")
    public List<FinancialDto> getFinancialsForYear(
            @PathVariable String ticker,
            @PathVariable int year
    ) {
        return financialQueryService.getAnnualForYear(ticker, year);
    }
}
