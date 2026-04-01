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
import java.time.LocalDate;
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

        return marketMetricService.load(stock, asOfDate)
                .map(this::toDto);
    }

    public MarketSnapshotDto toDto(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        return MarketSnapshotDto.builder()
                .asOfDate(snapshot.getAsOfDate())
                .marketCap(snapshot.getMarketCap())
                .floatMarketCap(snapshot.getFloatMarketCap())
                .per(MetricMath.toDouble(snapshot.getPer()))
                .pbr(MetricMath.toDouble(snapshot.getPbr()))
                .floatRatio(MetricMath.toDouble(snapshot.getFloatRatio()))
                .treasuryRatio(MetricMath.toDouble(snapshot.getTreasuryRatio()))
                .sharesOutstanding(snapshot.getSharesOutstanding())
                .source(snapshot.getSource())
                .build();
    }

    private MarketSnapshotDto toDto(MarketMetricResult result) {
        SnapshotMetricView snapshot = result.snapshot();

        return MarketSnapshotDto.builder()
                .asOfDate(snapshot != null ? snapshot.asOfDate() : null)
                .marketCap(result.realtime() != null ? result.realtime().marketCap() : null)
                .floatMarketCap(result.realtime() != null ? result.realtime().floatMarketCap() : null)
                .per(result.realtime() != null ? result.realtime().per() : null)
                .pbr(result.realtime() != null ? result.realtime().pbr() : null)
                .floatRatio(ratioPercentAsDouble(snapshot != null ? snapshot.floatingShares() : null,
                        snapshot != null ? snapshot.sharesOutstanding() : null))
                .treasuryRatio(ratioPercentAsDouble(snapshot != null ? snapshot.treasuryShares() : null,
                        snapshot != null ? snapshot.sharesOutstanding() : null))
                .sharesOutstanding(snapshot != null ? snapshot.sharesOutstanding() : null)
                .source(snapshot != null ? snapshot.source() : null)
                .build();
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
