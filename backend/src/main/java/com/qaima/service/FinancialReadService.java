package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.FinancialDto;
import com.qaima.mapper.FinancialMapper;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FinancialReadService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final FinancialMapper financialMapper;
    private static final String DEFAULT_EXCHANGE_CODE = "KOSPI";

    public Mono<List<FinancialDto>> getForLastNYears(
            String stockCode,
            String exchangeCode,
            PeriodType periodType,
            Integer periodNo,
            int years,
            LocalDate asOfDate
    ) {
        if (years <= 0) return Mono.error(new IllegalArgumentException("years는 1 이상이어야 합니다."));
        if (periodType == null) return Mono.error(new IllegalArgumentException("periodType 값은 필수입니다. (A/Q/H/TTM)"));
        validatePeriodNo(periodType, periodNo);

        int toYear = (asOfDate != null ? asOfDate : LocalDate.now()).getYear();
        int fromYear = toYear - (years - 1);

        String resolvedExchange = resolveExchangeCode(exchangeCode);
        return Blocking.call(() -> stockRepository
                        .findByExchangeCodeAndStockCodeIgnoreCase(resolvedExchange, stockCode)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown stockCode: " + stockCode + " (exchange=" + resolvedExchange + ")"
                        )))
                .flatMap(stock -> Blocking.call(() -> queryFinancials(stock, periodType, periodNo, fromYear, toYear)))
                .map(list -> list.stream().map(financialMapper::toDto).toList());
    }

    public Mono<List<FinancialDto>> getForYear(
            String stockCode,
            String exchangeCode,
            PeriodType periodType,
            Integer periodNo,
            int year
    ) {
        return getForLastNYears(stockCode, exchangeCode, periodType, periodNo, 1, LocalDate.of(year, 12, 31));
    }

    public Mono<List<FinancialDto>> getAnnualForLastNYears(
            String stockCode,
            String exchangeCode,
            int years,
            LocalDate asOfDate
    ) {
        return getForLastNYears(stockCode, exchangeCode, PeriodType.A, null, years, asOfDate);
    }

    public Mono<List<FinancialDto>> getAnnualForYear(String stockCode, String exchangeCode, int year) {
        return getForYear(stockCode, exchangeCode, PeriodType.A, null, year);
    }

    private List<Financial> queryFinancials(
            Stock stock, PeriodType periodType, Integer periodNo, int fromYear, int toYear
    ) {
        if (periodNo == null) {
            return financialRepository
                    .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
                            stock, periodType, fromYear, toYear
                    );
        }
        return financialRepository
                .findByStockAndPeriodTypeAndPeriodNoAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
                        stock, periodType, periodNo, fromYear, toYear
                );
    }

    /**
     * 범용 조회:
     * - periodType: A/Q/H/TTM
     * - periodNo: null이면 해당 periodType 전체(예: Q면 1~4 전부, H면 1~2 전부)
     *            값이 있으면 해당 periodNo만 필터링
     */

    private void validatePeriodNo(PeriodType periodType, Integer periodNo) {
        if (periodNo == null) return;

        switch (periodType) {
            case Q -> {
                if (periodNo < 1 || periodNo > 4) throw new IllegalArgumentException("Q(분기) periodNo는 1~4만 허용됩니다.");
            }
            case H -> {
                if (periodNo < 1 || periodNo > 2) throw new IllegalArgumentException("H(반기) periodNo는 1~2만 허용됩니다.");
            }
            case A -> {
                if (periodNo != 1) throw new IllegalArgumentException("A(연간) periodNo는 1만 허용됩니다.");
            }
            case TTM -> {
                if (periodNo != 0) throw new IllegalArgumentException("TTM periodNo는 0만 허용됩니다.");
            }
        }
    }

    private String resolveExchangeCode(String exchangeCode) {
        String normalized = normalizeExchangeCode(exchangeCode);
        return normalized != null ? normalized : DEFAULT_EXCHANGE_CODE;
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) {
            return null;
        }
        String trimmed = exchangeCode.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        return switch (trimmed.toUpperCase()) {
            case "XKRX", "KRX" -> "KOSPI";
            case "XKOS" -> "KOSDAQ";
            case "XNYS" -> "NYSE";
            case "XNAS" -> "NASDAQ";
            default -> trimmed.toUpperCase();
        };
    }
}
