package com.qaima.service.marketmetric;

import com.qaima.domain.Financial;
import com.qaima.service.marketmetric.model.MarketSnapshotInput;
import com.qaima.service.marketmetric.model.ShareBasisView;
import com.qaima.service.marketmetric.model.SnapshotMetricView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FinancialFallbackCalculator {

    private final SnapshotCalculator snapshotCalculator;

    public FinancialFallbackCalculator(SnapshotCalculator snapshotCalculator) {
        this.snapshotCalculator = snapshotCalculator;
    }

    public SnapshotMetricView calculate(List<Financial> financials, ShareBasisView shareBasis, LocalDate asOfDate) {
        SnapshotMetricView snapshot = snapshotCalculator.calculate(new MarketSnapshotInput(
                financials,
                shareBasis == null ? null : shareBasis.sharesOutstanding(),
                shareBasis == null ? null : shareBasis.valuationShares(),
                shareBasis == null ? null : shareBasis.floatingShares(),
                shareBasis == null ? null : shareBasis.treasuryShares(),
                asOfDate,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        List<String> warnings = new ArrayList<>();
        warnings.add("SNAPSHOT_FALLBACK_USED");
        warnings.addAll(snapshot.warnings() == null ? List.of() : snapshot.warnings());

        BigDecimal sharesOutstanding = firstNonNull(
                snapshot.sharesOutstanding(),
                shareBasis == null ? null : shareBasis.sharesOutstanding()
        );
        BigDecimal floatingShares = firstNonNull(
                snapshot.floatingShares(),
                shareBasis == null ? null : shareBasis.floatingShares()
        );
        BigDecimal treasuryShares = firstNonNull(
                snapshot.treasuryShares(),
                shareBasis == null ? null : shareBasis.treasuryShares()
        );

        return new SnapshotMetricView(
                snapshot.asOfDate(),
                sharesOutstanding,
                floatingShares,
                treasuryShares,
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
                warnings,
                "FINANCIAL_FALLBACK"
        );
    }

    private BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }
}
