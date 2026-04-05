package com.qaima.service.stock;

import com.qaima.common.Blocking;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import com.qaima.dto.stock.MarketSnapshotDto;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.MarketSnapshotRepository;
import com.qaima.service.marketmetric.MarketMetricService;
import com.qaima.service.marketmetric.MarketSnapshotCacheService;
import com.qaima.service.marketmetric.ShareBasisResolver;
import com.qaima.service.marketmetric.SnapshotCalculator;
import com.qaima.service.marketmetric.model.MarketMetricResult;
import com.qaima.service.marketmetric.model.MarketSnapshotInput;
import com.qaima.service.marketmetric.model.ShareBasisView;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import com.qaima.service.marketmetric.support.MetricMath;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSnapshotService {

    private static final String SOURCE_OPENDART_PRIMARY = "OPENDART_PRIMARY";
    private static final String SOURCE_BATCH = "BATCH_SNAPSHOT";
    private static final String SOURCE_KIS_SHARES_FALLBACK = "KIS_SHARES_FALLBACK";

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final FinancialRepository financialRepository;
    private final SnapshotCalculator snapshotCalculator;
    private final MarketSnapshotCacheService snapshotCacheService;
    private final MarketMetricService marketMetricService;
    private final ShareBasisResolver shareBasisResolver;
    private final PlatformTransactionManager transactionManager;

    public Mono<MarketSnapshot> upsertBatchSnapshot(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.empty();
        }

        LocalDate baseDate = asOfDate != null ? asOfDate : LocalDate.now();
        return Mono.zip(
                        Blocking.call(() -> financialRepository.findByStockOrderByReportDateDescVersionDesc(
                                stock,
                                PageRequest.of(0, 12)
                        )),
                        shareBasisResolver.resolve(stock, baseDate, true)
                )
                .flatMap(tuple -> Blocking.call(() -> tx().execute(status -> {
                    ShareBasisView shareBasis = tuple.getT2();
                    SnapshotMetricView calculated = snapshotCalculator.calculate(new MarketSnapshotInput(
                            tuple.getT1(),
                            shareBasis.sharesOutstanding(),
                            shareBasis.floatingShares(),
                            shareBasis.treasuryShares(),
                            baseDate,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null
                    ));

                    MarketSnapshot snapshot = marketSnapshotRepository
                            .findByStockAndAsOfDate(stock, baseDate)
                            .orElseGet(MarketSnapshot::new);

                    snapshot.setStock(stock);
                    snapshot.setAsOfDate(baseDate);
                    snapshot.setMarketCap(null);
                    snapshot.setFloatMarketCap(null);
                    snapshot.setPer(null);
                    snapshot.setPbr(null);
                    snapshot.setSharesOutstanding(calculated.sharesOutstanding());
                    snapshot.setEpsTtm(calculated.epsTtm());
                    snapshot.setBps(calculated.bps());
                    snapshot.setSps(calculated.sps());
                    snapshot.setRoe(calculated.roe());
                    snapshot.setRoa(calculated.roa());
                    snapshot.setOperatingMargin(calculated.operatingMargin());
                    snapshot.setNetMargin(calculated.netMargin());
                    snapshot.setDebtRatio(calculated.debtRatio());
                    snapshot.setCurrentAssets(calculated.currentAssets());
                    snapshot.setCurrentLiabilities(calculated.currentLiabilities());
                    snapshot.setInventory(calculated.inventory());
                    snapshot.setInterestExpense(calculated.interestExpense());
                    snapshot.setOperatingCashFlow(calculated.operatingCashFlow());
                    snapshot.setCapex(calculated.capex());
                    snapshot.setFloatRatio(ratioPercent(calculated.floatingShares(), calculated.sharesOutstanding()));
                    snapshot.setTreasuryRatio(ratioPercent(calculated.treasuryShares(), calculated.sharesOutstanding()));
                    snapshot.setWarningFlags(joinWarnings(calculated.warnings(), shareBasis.warnings()));
                    snapshot.setSource(resolveSnapshotSource(shareBasis));

                    return marketSnapshotRepository.save(snapshot);
                })))
                .flatMap(saved -> snapshotCacheService.cacheSnapshot(stock.getStockCode(), saved).thenReturn(saved))
                .doOnNext(saved -> log.info(
                        "[MarketSnapshotService] batch upsert complete. stockCode={}, asOfDate={}, source={}",
                        stock.getStockCode(),
                        saved.getAsOfDate(),
                        saved.getSource()
                ));
    }

    public Mono<MarketSnapshotDto> getLatestDto(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.justOrEmpty((MarketSnapshotDto) null);
        }

        LocalDate baseDate = asOfDate != null ? asOfDate : LocalDate.now();
        return marketMetricService.load(stock, asOfDate)
                .flatMap(result -> Blocking.call(() -> toDto(result, stock, baseDate)));
    }

    public MarketSnapshotDto toDto(MarketSnapshot snapshot) {
        return toDto(snapshot, null);
    }

    public MarketSnapshotDto toDto(MarketSnapshot snapshot, Stock stock) {
        if (snapshot == null) {
            return null;
        }
        GrowthMetrics growth = stock != null
                ? calculateGrowthMetrics(stock, snapshot.getAsOfDate(), snapshot.getSharesOutstanding())
                : GrowthMetrics.empty();

        return MarketSnapshotDto.builder()
                .asOfDate(snapshot.getAsOfDate())
                .marketCap(snapshot.getMarketCap())
                .floatMarketCap(snapshot.getFloatMarketCap())
                .per(MetricMath.toDouble(snapshot.getPer()))
                .pbr(MetricMath.toDouble(snapshot.getPbr()))
                .psr(MetricMath.toDouble(derivePsr(snapshot.getMarketCap(), snapshot.getSps(), snapshot.getSharesOutstanding())))
                .floatRatio(MetricMath.toDouble(snapshot.getFloatRatio()))
                .treasuryRatio(MetricMath.toDouble(snapshot.getTreasuryRatio()))
                .sharesOutstanding(snapshot.getSharesOutstanding())
                .epsTtm(snapshot.getEpsTtm())
                .bps(snapshot.getBps())
                .sps(snapshot.getSps())
                .roe(MetricMath.toDouble(snapshot.getRoe()))
                .roa(MetricMath.toDouble(snapshot.getRoa()))
                .operatingMargin(MetricMath.toDouble(snapshot.getOperatingMargin()))
                .netMargin(MetricMath.toDouble(snapshot.getNetMargin()))
                .debtRatio(MetricMath.toDouble(snapshot.getDebtRatio()))
                .currentRatio(MetricMath.toDouble(ratioPercent(snapshot.getCurrentAssets(), snapshot.getCurrentLiabilities())))
                .quickRatio(MetricMath.toDouble(quickRatio(snapshot.getCurrentAssets(), snapshot.getInventory(),
                        snapshot.getCurrentLiabilities())))
                .interestCoverageRatio(MetricMath.toDouble(interestCoverageRatio(
                        snapshot.getOperatingMargin(),
                        snapshot.getSps(),
                        snapshot.getSharesOutstanding(),
                        snapshot.getInterestExpense()
                )))
                .freeCashFlow(freeCashFlow(snapshot.getOperatingCashFlow(), snapshot.getCapex()))
                .revenueGrowth(growth.revenueGrowth())
                .epsGrowth(growth.epsGrowth())
                .source(snapshot.getSource())
                .build();
    }

    private MarketSnapshotDto toDto(MarketMetricResult result, Stock stock, LocalDate asOfDate) {
        SnapshotMetricView snapshot = result.snapshot();
        GrowthMetrics growth = calculateGrowthMetrics(stock, asOfDate, snapshot != null ? snapshot.sharesOutstanding() : null);

        return MarketSnapshotDto.builder()
                .asOfDate(snapshot != null ? snapshot.asOfDate() : null)
                .marketCap(result.realtime() != null ? result.realtime().marketCap() : null)
                .floatMarketCap(result.realtime() != null ? result.realtime().floatMarketCap() : null)
                .per(result.realtime() != null ? result.realtime().per() : null)
                .pbr(result.realtime() != null ? result.realtime().pbr() : null)
                .psr(result.realtime() != null ? result.realtime().psr() : null)
                .floatRatio(ratioPercentAsDouble(snapshot != null ? snapshot.floatingShares() : null,
                        snapshot != null ? snapshot.sharesOutstanding() : null))
                .treasuryRatio(ratioPercentAsDouble(snapshot != null ? snapshot.treasuryShares() : null,
                        snapshot != null ? snapshot.sharesOutstanding() : null))
                .sharesOutstanding(snapshot != null ? snapshot.sharesOutstanding() : null)
                .epsTtm(snapshot != null ? snapshot.epsTtm() : null)
                .bps(snapshot != null ? snapshot.bps() : null)
                .sps(snapshot != null ? snapshot.sps() : null)
                .roe(snapshot != null ? MetricMath.toDouble(snapshot.roe()) : null)
                .roa(snapshot != null ? MetricMath.toDouble(snapshot.roa()) : null)
                .operatingMargin(snapshot != null ? MetricMath.toDouble(snapshot.operatingMargin()) : null)
                .netMargin(snapshot != null ? MetricMath.toDouble(snapshot.netMargin()) : null)
                .debtRatio(snapshot != null ? MetricMath.toDouble(snapshot.debtRatio()) : null)
                .currentRatio(snapshot != null ? MetricMath.toDouble(
                        ratioPercent(snapshot.currentAssets(), snapshot.currentLiabilities())) : null)
                .quickRatio(snapshot != null ? MetricMath.toDouble(
                        quickRatio(snapshot.currentAssets(), snapshot.inventory(), snapshot.currentLiabilities())) : null)
                .interestCoverageRatio(snapshot != null ? MetricMath.toDouble(
                        interestCoverageRatio(snapshot.operatingMargin(), snapshot.sps(),
                                snapshot.sharesOutstanding(), snapshot.interestExpense())) : null)
                .freeCashFlow(snapshot != null ? freeCashFlow(snapshot.operatingCashFlow(), snapshot.capex()) : null)
                .revenueGrowth(growth.revenueGrowth())
                .epsGrowth(growth.epsGrowth())
                .source(snapshot != null ? snapshot.source() : null)
                .build();
    }

    private GrowthMetrics calculateGrowthMetrics(Stock stock, LocalDate asOfDate, BigDecimal currentSharesOutstanding) {
        if (stock == null) {
            return GrowthMetrics.empty();
        }

        List<com.qaima.domain.Financial> financials = financialRepository.findByStockOrderByReportDateDescVersionDesc(
                stock,
                PageRequest.of(0, 12)
        );
        if (financials == null || financials.isEmpty()) {
            return GrowthMetrics.empty();
        }

        List<com.qaima.domain.Financial> quarters = financials.stream()
                .filter(financial -> financial != null && financial.getPeriodType() == com.qaima.domain.PeriodType.Q)
                .sorted(financialComparator().reversed())
                .toList();

        TtmGrowthWindow ttmWindow = resolveTtmGrowthWindow(quarters);
        if (ttmWindow != null) {
            BigDecimal revenueGrowth = ratioGrowthPercent(ttmWindow.currentRevenue(), ttmWindow.previousRevenue());
            BigDecimal currentShares = currentSharesOutstanding != null
                    ? currentSharesOutstanding
                    : resolveSharesOutstanding(stock, asOfDate);
            BigDecimal previousShares = resolveSharesOutstanding(stock, resolveFinancialDate(ttmWindow.previousAnchor(), asOfDate));
            BigDecimal currentEps = MetricMath.divide(ttmWindow.currentNetIncome(), currentShares);
            BigDecimal previousEps = MetricMath.divide(ttmWindow.previousNetIncome(), previousShares);
            return new GrowthMetrics(
                    MetricMath.toDouble(revenueGrowth),
                    MetricMath.toDouble(ratioGrowthPercent(currentEps, previousEps))
            );
        }

        List<com.qaima.domain.Financial> annuals = financials.stream()
                .filter(financial -> financial != null && financial.getPeriodType() == com.qaima.domain.PeriodType.A)
                .sorted(financialComparator().reversed())
                .toList();
        if (annuals.size() < 2) {
            return GrowthMetrics.empty();
        }

        com.qaima.domain.Financial current = annuals.get(0);
        com.qaima.domain.Financial previous = annuals.get(1);
        BigDecimal previousShares = resolveSharesOutstanding(stock, resolveFinancialDate(previous, asOfDate));
        BigDecimal currentShares = resolveSharesOutstanding(stock, resolveFinancialDate(current, asOfDate));
        BigDecimal currentEps = MetricMath.divide(current.getNetIncome(), currentShares);
        BigDecimal previousEps = MetricMath.divide(previous.getNetIncome(), previousShares);

        return new GrowthMetrics(
                MetricMath.toDouble(ratioGrowthPercent(current.getRevenue(), previous.getRevenue())),
                MetricMath.toDouble(ratioGrowthPercent(currentEps, previousEps))
        );
    }

    private BigDecimal resolveSharesOutstanding(Stock stock, LocalDate effectiveDate) {
        return shareBasisResolver.fromIssuedShares(
                shareBasisResolver.findPrimaryIssuedShares(stock, effectiveDate)
        ).sharesOutstanding();
    }

    private LocalDate resolveFinancialDate(com.qaima.domain.Financial financial, LocalDate fallback) {
        if (financial == null) {
            return fallback;
        }
        return financial.getReportDate() != null ? financial.getReportDate() : fallback;
    }

    private TtmGrowthWindow resolveTtmGrowthWindow(List<com.qaima.domain.Financial> quarters) {
        if (quarters == null || quarters.size() < 8) {
            return null;
        }

        List<com.qaima.domain.Financial> currentFour = latestContiguousFourQuarters(quarters, 0);
        List<com.qaima.domain.Financial> previousFour = latestContiguousFourQuarters(quarters, 4);
        if (currentFour == null || previousFour == null) {
            return null;
        }

        return new TtmGrowthWindow(
                sumFinancialField(currentFour, com.qaima.domain.Financial::getRevenue),
                sumFinancialField(previousFour, com.qaima.domain.Financial::getRevenue),
                sumFinancialField(currentFour, com.qaima.domain.Financial::getNetIncome),
                sumFinancialField(previousFour, com.qaima.domain.Financial::getNetIncome),
                previousFour.get(0)
        );
    }

    private List<com.qaima.domain.Financial> latestContiguousFourQuarters(
            List<com.qaima.domain.Financial> quarters,
            int startIndex
    ) {
        if (quarters == null || quarters.size() < startIndex + 4) {
            return null;
        }

        List<com.qaima.domain.Financial> slice = quarters.subList(startIndex, startIndex + 4);
        for (int i = 0; i < slice.size() - 1; i++) {
            if (!isPreviousQuarter(slice.get(i), slice.get(i + 1))) {
                return null;
            }
        }
        return slice;
    }

    private boolean isPreviousQuarter(com.qaima.domain.Financial current, com.qaima.domain.Financial next) {
        int currentYear = current.getFiscalYear();
        int currentQuarter = current.getPeriodNo();
        int expectedYear = currentQuarter == 1 ? currentYear - 1 : currentYear;
        int expectedQuarter = currentQuarter == 1 ? 4 : currentQuarter - 1;
        return next.getFiscalYear() == expectedYear && next.getPeriodNo() == expectedQuarter;
    }

    private Comparator<com.qaima.domain.Financial> financialComparator() {
        return Comparator
                .comparing(com.qaima.domain.Financial::getFiscalYear)
                .thenComparing(com.qaima.domain.Financial::getPeriodNo)
                .thenComparing(com.qaima.domain.Financial::getReportDate, Comparator.nullsLast(LocalDate::compareTo));
    }

    private BigDecimal sumFinancialField(
            List<com.qaima.domain.Financial> financials,
            java.util.function.Function<com.qaima.domain.Financial, BigDecimal> getter
    ) {
        BigDecimal total = BigDecimal.ZERO;
        for (com.qaima.domain.Financial financial : financials) {
            BigDecimal value = getter.apply(financial);
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return total;
    }

    private BigDecimal ratioGrowthPercent(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal quickRatio(BigDecimal currentAssets, BigDecimal inventory, BigDecimal currentLiabilities) {
        if (currentAssets == null || currentLiabilities == null || currentLiabilities.signum() == 0) {
            return null;
        }
        BigDecimal quickAssets = inventory == null ? currentAssets : currentAssets.subtract(inventory);
        return ratioPercent(quickAssets, currentLiabilities);
    }

    private BigDecimal interestCoverageRatio(
            BigDecimal operatingMargin,
            BigDecimal sps,
            BigDecimal sharesOutstanding,
            BigDecimal interestExpense
    ) {
        if (operatingMargin == null || sps == null || sharesOutstanding == null
                || interestExpense == null || interestExpense.signum() == 0) {
            return null;
        }
        BigDecimal revenue = sps.multiply(sharesOutstanding);
        BigDecimal operatingIncome = revenue
                .multiply(operatingMargin)
                .movePointLeft(2);
        return MetricMath.divide(operatingIncome, interestExpense);
    }

    private BigDecimal freeCashFlow(BigDecimal operatingCashFlow, BigDecimal capex) {
        if (operatingCashFlow == null || capex == null) {
            return null;
        }
        return operatingCashFlow.subtract(capex);
    }

    private BigDecimal derivePsr(BigDecimal marketCap, BigDecimal sps, BigDecimal sharesOutstanding) {
        if (marketCap == null || sps == null || sharesOutstanding == null) {
            return null;
        }
        return MetricMath.divide(marketCap, sps.multiply(sharesOutstanding));
    }

    private record GrowthMetrics(Double revenueGrowth, Double epsGrowth) {
        private static GrowthMetrics empty() {
            return new GrowthMetrics(null, null);
        }
    }

    private record TtmGrowthWindow(
            BigDecimal currentRevenue,
            BigDecimal previousRevenue,
            BigDecimal currentNetIncome,
            BigDecimal previousNetIncome,
            com.qaima.domain.Financial previousAnchor
    ) {
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private BigDecimal ratioPercent(BigDecimal numerator, BigDecimal denominator) {
        return com.qaima.service.marketmetric.support.MetricMath.ratioPercent(numerator, denominator);
    }

    private Double ratioPercentAsDouble(BigDecimal numerator, BigDecimal denominator) {
        return MetricMath.toDouble(ratioPercent(numerator, denominator));
    }

    private String resolveSnapshotSource(ShareBasisView shareBasis) {
        if (shareBasis == null) {
            return SOURCE_BATCH;
        }
        if (SOURCE_KIS_SHARES_FALLBACK.equals(shareBasis.source())) {
            return SOURCE_KIS_SHARES_FALLBACK;
        }
        if (shareBasis.issuedSharesTotal() != null) {
            return SOURCE_OPENDART_PRIMARY;
        }
        return SOURCE_BATCH;
    }

    private String joinWarnings(java.util.List<String>... warningGroups) {
        return java.util.Arrays.stream(warningGroups)
                .filter(group -> group != null)
                .flatMap(java.util.Collection::stream)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .collect(Collectors.joining(","));
    }
}
