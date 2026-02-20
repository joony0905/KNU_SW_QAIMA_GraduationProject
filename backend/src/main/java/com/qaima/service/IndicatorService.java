package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.Exchange;
import com.qaima.domain.Financial;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.dto.IndicatorSnapshotDto;
import com.qaima.external.KrStockClient;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.MarketSnapshotRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class IndicatorService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final MarketSnapshotService marketSnapshotService;
    private final KrStockClient krStockClient;
    private static final String DEFAULT_EXCHANGE_CODE = "KOSPI";

    public Mono<IndicatorSnapshotDto> getIndicators(
            String stockCode,
            String exchangeCode,
            PeriodType periodType,
            LocalDate asOfDate
    ) {
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        String resolvedExchange = resolveExchangeCode(exchangeCode);
        return Blocking.call(() -> stockRepository
                        .findByExchangeCodeAndStockCodeIgnoreCase(resolvedExchange, stockCode)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown stockCode: " + stockCode + " (exchange=" + resolvedExchange + ")"
                        )))
                .flatMap(stock ->
                        ensureSnapshotIfNeeded(stock, asOfDate)
                                .then(Blocking.call(() -> buildSnapshot(stock, periodType, asOfDate)))
                );
    }

    private IndicatorSnapshotDto buildSnapshot(Stock stock, PeriodType periodType, LocalDate asOfDate) {
        MarketSnapshot snapshot = loadSnapshot(stock, asOfDate);
        Financial financial = loadFinancial(stock, periodType);

        if (snapshot == null && financial == null) {
            throw new ResourceNotFoundException("No indicator data for stockCode: " + stock.getStockCode());
        }

        Double per = (snapshot != null) ? bdToDouble(snapshot.getPer()) : null;
        Double pbr = (snapshot != null) ? bdToDouble(snapshot.getPbr()) : null;

        if (per == null && snapshot != null && financial != null) {
            per = ratio(snapshot.getMarketCap(), financial.getNetIncome());
        }
        if (pbr == null && snapshot != null && financial != null) {
            pbr = ratio(snapshot.getMarketCap(), financial.getEquity());
        }

        Double operatingMargin = (financial != null)
                ? ratioPct(financial.getOperatingIncome(), financial.getRevenue())
                : null;
        Double netMargin = (financial != null)
                ? ratioPct(financial.getNetIncome(), financial.getRevenue())
                : null;
        Double roe = (financial != null)
                ? ratioPct(financial.getNetIncome(), financial.getEquity())
                : null;
        Double debtRatio = (financial != null)
                ? ratioPct(financial.getLiabilities(), financial.getEquity())
                : null;

        return IndicatorSnapshotDto.builder()
                .stockCode(stock.getStockCode())
                .asOfDate(snapshot != null ? snapshot.getAsOfDate() : asOfDate)
                .periodType(financial != null ? financial.getPeriodType().name() : null)
                .fiscalYear(financial != null ? financial.getFiscalYear() : null)
                .periodNo(financial != null ? financial.getPeriodNo() : null)
                .reportDate(financial != null ? financial.getReportDate() : null)
                .per(per)
                .pbr(pbr)
                .roe(roe)
                .operatingMargin(operatingMargin)
                .netMargin(netMargin)
                .debtRatio(debtRatio)
                .build();
    }

    private MarketSnapshot loadSnapshot(Stock stock, LocalDate asOfDate) {
        if (asOfDate == null) {
            return marketSnapshotRepository.findTopByStockOrderByAsOfDateDesc(stock).orElse(null);
        }
        return marketSnapshotRepository
                .findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(stock, asOfDate)
                .orElse(null);
    }

    private Financial loadFinancial(Stock stock, PeriodType periodType) {
        if (periodType != null) {
            return latestFinancial(stock, periodType);
        }

        Financial financial = latestFinancial(stock, PeriodType.TTM);
        if (financial != null) return financial;

        financial = latestFinancial(stock, PeriodType.A);
        if (financial != null) return financial;

        financial = latestFinancial(stock, PeriodType.Q);
        if (financial != null) return financial;

        return latestFinancial(stock, PeriodType.H);
    }

    private Financial latestFinancial(Stock stock, PeriodType periodType) {
        return financialRepository
                .findTopByStockAndPeriodTypeOrderByFiscalYearDescPeriodNoDescVersionDesc(stock, periodType)
                .orElse(null);
    }

    private static Double ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null) return null;
        if (denominator.signum() == 0) return null;
        return numerator
                .divide(denominator, 6, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static Double ratioPct(BigDecimal numerator, BigDecimal denominator) {
        Double ratio = ratio(numerator, denominator);
        return (ratio == null) ? null : ratio * 100.0;
    }

    private static Double bdToDouble(BigDecimal v) {
        return v == null ? null : v.doubleValue();
    }

    private Mono<Void> ensureSnapshotIfNeeded(Stock stock, LocalDate asOfDate) {
        if (!isKisEligible(stock) || !isTodayOrNull(asOfDate)) {
            return Mono.empty();
        }

        return Blocking.call(() -> loadSnapshot(stock, asOfDate))
                .flatMap(snapshot -> {
                    if (!isSnapshotIncomplete(snapshot)) {
                        return Mono.empty();
                    }

                    String marketDivCode = toKisMarketDivCode(stock.getExchange());
                    if ("B".equals(marketDivCode)) {
                        return Mono.empty();
                    }

                    return krStockClient
                            .fetchKisStatRaw(stock.getStockCode(), marketDivCode)
                            .flatMap(output -> marketSnapshotService.upsertFromKis(stock, output, asOfDate))
                            .then()
                            .onErrorResume(e -> Mono.empty());
                });
    }

    private boolean isSnapshotIncomplete(MarketSnapshot snapshot) {
        if (snapshot == null) return true;
        return snapshot.getMarketCap() == null
                || snapshot.getPer() == null
                || snapshot.getPbr() == null;
    }

    private boolean isKisEligible(Stock stock) {
        if (stock == null || stock.getExchange() == null) return false;
        String code = stock.getExchange().getCode();
        if (code == null || code.isBlank()) return false;

        return switch (code.trim().toUpperCase()) {
            case "KRX", "KOSPI", "KOSDAQ", "KONEX" -> true;
            default -> false;
        };
    }

    private boolean isTodayOrNull(LocalDate asOfDate) {
        return asOfDate == null || LocalDate.now().equals(asOfDate);
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null || exchange.getCode() == null) return "B";
        return switch (exchange.getCode().toUpperCase()) {
            case "KRX", "XKRX", "KOSPI" -> "J";
            case "KOSDAQ", "XKOS" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
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
