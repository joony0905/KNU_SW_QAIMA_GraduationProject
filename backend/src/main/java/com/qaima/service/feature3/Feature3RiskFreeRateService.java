package com.qaima.service.feature3;

import com.qaima.domain.BondYield;
import com.qaima.service.bondyield.BondYieldInstrument;
import com.qaima.service.bondyield.BondYieldSyncService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class Feature3RiskFreeRateService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final BondYieldInstrument DEFAULT_INSTRUMENT = BondYieldInstrument.KR3Y;

    private final BondYieldSyncService bondYieldSyncService;

    public Mono<RiskFreeRate> resolve() {
        LocalDate today = LocalDate.now(SEOUL);
        return bondYieldSyncService.findLatest(DEFAULT_INSTRUMENT, today)
                .map(row -> toRiskFreeRate(row, "DB_BOND_YIELD"))
                .switchIfEmpty(bondYieldSyncService.syncLatest(DEFAULT_INSTRUMENT)
                        .map(row -> toRiskFreeRate(row, "BOK_API_BOND_YIELD")))
                .onErrorResume(ex -> {
                    log.warn("[Feature3RiskFreeRate] risk-free rate resolve failed. cause={}", ex.getMessage(), ex);
                    return Mono.just(new RiskFreeRate(0.0, "DEFAULT_ZERO", null, null, null));
                });
    }

    private RiskFreeRate toRiskFreeRate(BondYield row, String source) {
        if (row == null || row.getYieldValue() == null) {
            return new RiskFreeRate(0.0, "DEFAULT_ZERO", null, null, null);
        }
        double decimalRate = row.getYieldValue()
                .divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP)
                .doubleValue();
        return new RiskFreeRate(
                decimalRate,
                source,
                row.getYieldDate() != null ? row.getYieldDate().toString() : null,
                row.getInstrumentCode(),
                row.getInstrumentName()
        );
    }

    public record RiskFreeRate(
            Double rate,
            String source,
            String asOf,
            String instrumentCode,
            String instrumentName
    ) {
    }
}
