package com.qaima.service.financial;

import com.qaima.common.Blocking;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.financial.FinancialDto;
import com.qaima.mapper.FinancialMapper;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.marketmetric.RealtimePriceService;
import com.qaima.service.marketmetric.ShareBasisResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FinancialReadService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final FinancialMapper financialMapper;
    private final RealtimePriceService realtimePriceService;
    private final ShareBasisResolver shareBasisResolver;

    public Mono<List<FinancialDto>> getForLastNYears(
            String stockCode,
            PeriodType periodType,
            Integer periodNo,
            int years,
            LocalDate asOfDate
    ) {
        if (years <= 0) {
            return Mono.error(new IllegalArgumentException("years는 1 이상이어야 합니다."));
        }
        if (periodType == null) {
            return Mono.error(new IllegalArgumentException("periodType 값은 필수입니다. (A/Q/H/TTM)"));
        }
        validatePeriodNo(periodType, periodNo);

        LocalDate snapshotAsOfDate = asOfDate != null ? asOfDate : LocalDate.now();
        int toYear = snapshotAsOfDate.getYear();
        int fromYear = toYear - (years - 1);

        return Blocking.call(() -> stockRepository.findByStockCodeWithExchange(stockCode)
                        .orElseThrow(() -> new ResourceNotFoundException("Unknown stockCode: " + stockCode)))
                .flatMap(stock -> realtimePriceService.getPrice(stock)
                        .map(Optional::of)
                        .onErrorResume(ex -> Mono.empty())
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(currentPriceOpt -> Blocking.call(() -> {
                            BigDecimal currentPrice = currentPriceOpt.orElse(null);
                            List<Financial> financials = queryFinancials(stock, periodType, periodNo, fromYear, toYear);
                            List<FinancialDto> dtos = financials.stream()
                                    .map(financial -> {
                                        BigDecimal shares = resolveValuationShares(stock, financial, snapshotAsOfDate);
                                        BigDecimal marketCap = (currentPrice != null && shares != null)
                                                ? currentPrice.multiply(shares)
                                                : null;
                                        return financialMapper.toDto(financial, marketCap, currentPrice, shares);
                                    })
                                    .toList();
                            applyGrowthMetrics(stock, financials, dtos, snapshotAsOfDate);
                            return dtos;
                        })));
    }

    public Mono<List<FinancialDto>> getForYear(
            String stockCode,
            PeriodType periodType,
            Integer periodNo,
            int year
    ) {
        return getForLastNYears(stockCode, periodType, periodNo, 1, LocalDate.of(year, 12, 31));
    }

    public Mono<List<FinancialDto>> getAnnualForLastNYears(String stockCode, int years, LocalDate asOfDate) {
        return getForLastNYears(stockCode, PeriodType.A, null, years, asOfDate);
    }

    public Mono<List<FinancialDto>> getAnnualForYear(String stockCode, int year) {
        return getForYear(stockCode, PeriodType.A, null, year);
    }

    private List<Financial> queryFinancials(
            Stock stock,
            PeriodType periodType,
            Integer periodNo,
            int fromYear,
            int toYear
    ) {
        if (periodNo == null) {
            return financialRepository
                    .findByStockAndPeriodTypeAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
                            stock,
                            periodType,
                            fromYear,
                            toYear
                    );
        }
        return financialRepository
                .findByStockAndPeriodTypeAndPeriodNoAndFiscalYearBetweenOrderByFiscalYearDescPeriodNoDesc(
                        stock,
                        periodType,
                        periodNo,
                        fromYear,
                        toYear
                );
    }

    private BigDecimal resolveValuationShares(Stock stock, Financial financial, LocalDate fallbackDate) {
        if (stock == null || financial == null) {
            return null;
        }

        LocalDate reportDate = financial.getReportDate();
        LocalDate effectiveDate = reportDate != null ? reportDate : fallbackDate;
        return shareBasisResolver.resolveFromIssuedShares(stock, effectiveDate).valuationShares();
    }

    private void validatePeriodNo(PeriodType periodType, Integer periodNo) {
        if (periodNo == null) {
            return;
        }

        switch (periodType) {
            case Q -> {
                if (periodNo < 1 || periodNo > 4) {
                    throw new IllegalArgumentException("Q(분기) periodNo는 1~4만 허용됩니다.");
                }
            }
            case H -> {
                if (periodNo < 1 || periodNo > 2) {
                    throw new IllegalArgumentException("H(반기) periodNo는 1~2만 허용됩니다.");
                }
            }
            case A -> {
                if (periodNo != 1) {
                    throw new IllegalArgumentException("A(연간) periodNo는 1만 허용됩니다.");
                }
            }
            case TTM -> {
                if (periodNo != 0) {
                    throw new IllegalArgumentException("TTM periodNo는 0만 허용됩니다.");
                }
            }
        }
    }

    private void applyGrowthMetrics(
            Stock stock,
            List<Financial> financials,
            List<FinancialDto> dtos,
            LocalDate fallbackDate
    ) {
        if (financials == null || financials.isEmpty() || dtos == null || dtos.isEmpty()) {
            return;
        }

        Map<String, Financial> byKey = new HashMap<>();
        for (Financial financial : financials) {
            byKey.put(financialKey(financial), financial);
        }

        Map<Long, BigDecimal> sharesByFinancialId = new HashMap<>();
        for (Financial financial : financials) {
            LocalDate effectiveDate = financial.getReportDate() != null ? financial.getReportDate() : fallbackDate;
            sharesByFinancialId.put(financial.getFinancialId(), resolveValuationShares(stock, financial, effectiveDate));
        }

        for (int i = 0; i < financials.size() && i < dtos.size(); i++) {
            Financial current = financials.get(i);
            FinancialDto dto = dtos.get(i);
            Financial previous = previousComparableFinancial(current, byKey);
            if (previous == null) {
                continue;
            }

            dto.setRevenueGrowth(growthPercent(current.getRevenue(), previous.getRevenue()));

            BigDecimal currentShares = sharesByFinancialId.get(current.getFinancialId());
            BigDecimal previousShares = sharesByFinancialId.get(previous.getFinancialId());
            BigDecimal currentEps = epsValue(current, currentShares);
            BigDecimal previousEps = epsValue(previous, previousShares);
            dto.setEpsGrowth(growthPercent(currentEps, previousEps));
        }
    }

    private Financial previousComparableFinancial(Financial current, Map<String, Financial> byKey) {
        if (current == null || current.getPeriodType() == null) {
            return null;
        }
        return byKey.get(current.getPeriodType().name() + ":" + (current.getFiscalYear() - 1) + ":" + current.getPeriodNo());
    }

    private String financialKey(Financial financial) {
        return financial.getPeriodType().name() + ":" + financial.getFiscalYear() + ":" + financial.getPeriodNo();
    }

    private Double growthPercent(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 8, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private BigDecimal epsValue(Financial financial, BigDecimal shares) {
        if (financial == null || shares == null || shares.signum() == 0 || financial.getNetIncome() == null) {
            return null;
        }
        return financial.getNetIncome().divide(shares, 4, java.math.RoundingMode.HALF_UP);
    }
}
