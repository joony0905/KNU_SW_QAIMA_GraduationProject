package com.qaima.service.marketmetric;

import com.qaima.common.Blocking;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.Stock;
import com.qaima.repository.FinancialRepository;
import com.qaima.service.marketmetric.model.MarketMetricResult;
import com.qaima.service.marketmetric.model.PriceQuoteResult;
import com.qaima.service.marketmetric.model.ShareBasisView;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MarketMetricService {

    private final MarketSnapshotCacheService snapshotCacheService;
    private final RealtimePriceService realtimePriceService;
    private final RealtimeRatioCalculator realtimeRatioCalculator;
    private final FinancialFallbackCalculator financialFallbackCalculator;
    private final FinancialRepository financialRepository;
    private final ShareBasisResolver shareBasisResolver;

    public Mono<MarketMetricResult> load(Stock stock, LocalDate asOfDate) {
        LocalDate baseDate = asOfDate != null ? asOfDate : LocalDate.now();

        return snapshotCacheService.getSnapshot(stock, baseDate)
                .defaultIfEmpty(emptySnapshot(baseDate))
                .flatMap(snapshot -> realtimePriceService.getPriceQuote(stock)
                        .flatMap(priceQuote -> {
                            List<String> warnings = new ArrayList<>(priceQuote.warnings());
                            if (snapshot.hasCoreFields()) {
                                warnings.addAll(snapshot.warnings());
                                return Mono.just(new MarketMetricResult(
                                        snapshot,
                                        realtimeRatioCalculator.calculate(priceQuote.price(), snapshot),
                                        dedupe(warnings),
                                        false
                                ));
                            }

                            return loadFallbackSnapshot(stock, baseDate, snapshot.warnings())
                                    .map(fallbackSnapshot -> {
                                        warnings.addAll(fallbackSnapshot.warnings());
                                        return new MarketMetricResult(
                                                fallbackSnapshot,
                                                realtimeRatioCalculator.calculate(priceQuote.price(), fallbackSnapshot),
                                                dedupe(warnings),
                                                true
                                        );
                                    });
                        }));
    }

    private Mono<SnapshotMetricView> loadFallbackSnapshot(Stock stock, LocalDate asOfDate, List<String> inheritedWarnings) {
        return Mono.zip(
                Blocking.call(() -> financialRepository.findByStockOrderByReportDateDescVersionDesc(
                        stock,
                        org.springframework.data.domain.PageRequest.of(0, 8)
                )),
                shareBasisResolver.resolve(stock, asOfDate, true)
        ).map(tuple -> mergeWarnings(
                financialFallbackCalculator.calculate(tuple.getT1(), tuple.getT2().sharesOutstanding(), asOfDate),
                inheritedWarnings,
                tuple.getT2().warnings()
        ));
    }

    private SnapshotMetricView emptySnapshot(LocalDate asOfDate) {
        return new SnapshotMetricView(
                asOfDate, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, List.of(), "SNAPSHOT_CACHE_MISS"
        );
    }

    private List<String> dedupe(List<String> warnings) {
        return new ArrayList<>(new LinkedHashSet<>(warnings));
    }

    private SnapshotMetricView mergeWarnings(
            SnapshotMetricView snapshot,
            List<String> inheritedWarnings,
            List<String> shareWarnings
    ) {
        List<String> warnings = new ArrayList<>();
        warnings.addAll(inheritedWarnings == null ? List.of() : inheritedWarnings);
        warnings.addAll(shareWarnings == null ? List.of() : shareWarnings);
        warnings.addAll(snapshot.warnings() == null ? List.of() : snapshot.warnings());
        return new SnapshotMetricView(
                snapshot.asOfDate(),
                snapshot.sharesOutstanding(),
                snapshot.floatingShares(),
                snapshot.treasuryShares(),
                snapshot.epsTtm(),
                snapshot.bps(),
                snapshot.sps(),
                snapshot.roe(),
                snapshot.roa(),
                snapshot.operatingMargin(),
                snapshot.netMargin(),
                snapshot.debtRatio(),
                snapshot.currentAssets(),
                snapshot.currentLiabilities(),
                snapshot.inventory(),
                snapshot.interestExpense(),
                snapshot.operatingCashFlow(),
                snapshot.capex(),
                dedupe(warnings),
                snapshot.source()
        );
    }
}
