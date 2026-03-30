package com.qaima.service.financial;

import com.qaima.common.Blocking;
import com.qaima.domain.Financial;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.PeriodType;
import com.qaima.domain.ShareClass;
import com.qaima.domain.Stock;
import com.qaima.dto.financial.FinancialDto;
import com.qaima.mapper.FinancialMapper;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.IssuedSharesRepository;
import com.qaima.repository.MarketSnapshotRepository;
import com.qaima.repository.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class FinancialReadService {

    private static final String SHARE_TYPE_COMMON = "보통주";
    private static final String SHARE_TYPE_PREFERRED = "우선주";
    private static final String SHARE_TYPE_TOTAL = "합계";

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final IssuedSharesRepository issuedSharesRepository;
    private final FinancialMapper financialMapper;

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
                        .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + stockCode)))
                .flatMap(stock -> Blocking.call(() -> {
                    List<Financial> financials = queryFinancials(stock, periodType, periodNo, fromYear, toYear);
                    MarketContext marketContext = resolveLatestMarketContext(stock, snapshotAsOfDate);
                    return financials.stream()
                            .map(financial -> financialMapper.toDto(
                                    financial,
                                    marketContext.marketCap(),
                                    marketContext.currentPrice(),
                                    resolveValuationShares(stock, financial, snapshotAsOfDate)
                            ))
                            .toList();
                }));
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

    private MarketContext resolveLatestMarketContext(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return new MarketContext(null, null);
        }

        MarketSnapshot snapshot = marketSnapshotRepository
                .findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(stock, asOfDate)
                .orElseGet(() -> marketSnapshotRepository.findTopByStockOrderByAsOfDateDesc(stock).orElse(null));
        if (snapshot == null) {
            return new MarketContext(null, null);
        }

        BigDecimal currentPrice = null;
        if (snapshot.getMarketCap() != null
                && snapshot.getSharesOutstanding() != null
                && snapshot.getSharesOutstanding().signum() != 0) {
            currentPrice = snapshot.getMarketCap()
                    .divide(snapshot.getSharesOutstanding(), 8, RoundingMode.HALF_UP);
        }

        return new MarketContext(snapshot.getMarketCap(), currentPrice);
    }

    private BigDecimal resolveValuationShares(Stock stock, Financial financial, LocalDate fallbackDate) {
        if (stock == null || financial == null) {
            return null;
        }

        LocalDate reportDate = financial.getReportDate();
        IssuedShares issuedShares;
        if (stock.getShareClass() == ShareClass.PREFERRED) {
            issuedShares = resolveIssuedShares(stock, SHARE_TYPE_PREFERRED, reportDate, fallbackDate);
        } else {
            issuedShares = resolveIssuedShares(stock, SHARE_TYPE_COMMON, reportDate, fallbackDate);
            if (issuedShares == null) {
                issuedShares = resolveIssuedShares(stock, SHARE_TYPE_TOTAL, reportDate, fallbackDate);
            }
        }

        return issuedShares != null && issuedShares.getIssuedSharesTotal() != null
                ? BigDecimal.valueOf(issuedShares.getIssuedSharesTotal())
                : null;
    }

    private IssuedShares resolveIssuedShares(
            Stock stock,
            String shareType,
            LocalDate primaryDate,
            LocalDate fallbackDate
    ) {
        IssuedShares issuedShares = findIssuedShares(stock, shareType, primaryDate);
        if (issuedShares == null
                && fallbackDate != null
                && (primaryDate == null || !fallbackDate.equals(primaryDate))) {
            issuedShares = findIssuedShares(stock, shareType, fallbackDate);
        }
        return issuedShares;
    }

    private IssuedShares findIssuedShares(Stock stock, String shareType, LocalDate asOfDate) {
        if (stock == null || shareType == null || asOfDate == null) {
            return null;
        }

        return issuedSharesRepository
                .findTopByStockAndShareTypeAndIssuedSharesTotalIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                        stock,
                        shareType,
                        asOfDate
                )
                .orElse(null);
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

    private record MarketContext(BigDecimal marketCap, BigDecimal currentPrice) {
    }
}
