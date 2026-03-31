package com.qaima.service.stock;

import com.qaima.common.Blocking;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.ShareClass;
import com.qaima.domain.Stock;
import com.qaima.dto.kis.KisStatResponseDto;
import com.qaima.dto.stock.MarketSnapshotDto;
import com.qaima.repository.IssuedSharesRepository;
import com.qaima.repository.MarketSnapshotRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketSnapshotService {

    private static final String SHARE_TYPE_COMMON = "보통주";
    private static final String SHARE_TYPE_PREFERRED = "우선주";
    private static final String SHARE_TYPE_TOTAL = "합계";
    private static final String SOURCE_KIS = "KIS";
    private static final String SOURCE_OPENDART_PRIMARY = "OPENDART_PRIMARY";

    private final MarketSnapshotRepository marketSnapshotRepository;
    private final IssuedSharesRepository issuedSharesRepository;
    private final PlatformTransactionManager transactionManager;

    public Mono<MarketSnapshot> upsertFromKis(Stock stock, KisStatResponseDto.Output output, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.error(new IllegalArgumentException("stock is required"));
        }
        if (output == null) {
            return Mono.error(new IllegalArgumentException("kis output is required"));
        }

        return Blocking.call(() -> tx().execute(status -> {
            LocalDate baseDate = asOfDate != null ? asOfDate : LocalDate.now();

            MarketSnapshot snapshot = marketSnapshotRepository
                    .findByStockAndAsOfDate(stock, baseDate)
                    .orElseGet(MarketSnapshot::new);
            IssuedShares primaryIssuedShares = resolvePrimaryIssuedShares(stock, baseDate);

            snapshot.setStock(stock);
            snapshot.setAsOfDate(baseDate);

            BigDecimal marketCap = parseNullableBigDecimal(output.getHts_avls());
            BigDecimal per = parseNullableBigDecimal(output.getPer());
            BigDecimal pbr = parseNullableBigDecimal(output.getPbr());
            BigDecimal kisSharesOutstanding = parseNullableBigDecimal(output.getLstn_stcn());
            BigDecimal dartSharesOutstanding = toBigDecimal(
                    primaryIssuedShares != null ? primaryIssuedShares.getIssuedSharesTotal() : null
            );
            BigDecimal floatingShares = toBigDecimal(
                    primaryIssuedShares != null ? primaryIssuedShares.getFloatingShares() : null
            );
            BigDecimal treasuryShares = toBigDecimal(
                    primaryIssuedShares != null ? primaryIssuedShares.getTreasuryShares() : null
            );
            BigDecimal sharesOutstanding = firstNonNull(dartSharesOutstanding, kisSharesOutstanding);

            BigDecimal price = parseNullableBigDecimal(output.getStck_prpr());
            BigDecimal eps = parseNullableBigDecimal(output.getEps());
            BigDecimal bps = parseNullableBigDecimal(output.getBps());
            BigDecimal floatMarketCap = null;
            BigDecimal floatRatio = null;
            BigDecimal treasuryRatio = null;

            if (marketCap != null) {
                marketCap = marketCap.multiply(BigDecimal.valueOf(100_000_000L));
            }
            if (price != null && sharesOutstanding != null) {
                marketCap = price.multiply(sharesOutstanding);
            }
            if (price != null && floatingShares != null) {
                floatMarketCap = price.multiply(floatingShares);
            }
            if (dartSharesOutstanding != null && dartSharesOutstanding.signum() != 0) {
                if (floatingShares != null) {
                    floatRatio = floatingShares
                            .divide(dartSharesOutstanding, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                }
                if (treasuryShares != null) {
                    treasuryRatio = treasuryShares
                            .divide(dartSharesOutstanding, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                }
            }
            if (per == null && price != null && eps != null && eps.signum() != 0) {
                per = price.divide(eps, 4, RoundingMode.HALF_UP);
            }
            if (pbr == null && price != null && bps != null && bps.signum() != 0) {
                pbr = price.divide(bps, 4, RoundingMode.HALF_UP);
            }

            snapshot.setMarketCap(marketCap);
            snapshot.setFloatMarketCap(floatMarketCap);
            snapshot.setPer(per);
            snapshot.setPbr(pbr);
            snapshot.setFloatRatio(floatRatio);
            snapshot.setTreasuryRatio(treasuryRatio);
            snapshot.setSharesOutstanding(sharesOutstanding);
            snapshot.setSource(dartSharesOutstanding != null ? SOURCE_OPENDART_PRIMARY : SOURCE_KIS);

            MarketSnapshot saved = marketSnapshotRepository.save(snapshot);
            log.info(
                    "[MarketSnapshotService] upsert complete: stockCode={}, asOfDate={}, source={}, shareType={}, dartSharesOutstanding={}, kisSharesOutstanding={}",
                    stock.getStockCode(),
                    baseDate,
                    saved.getSource(),
                    primaryIssuedShares != null ? primaryIssuedShares.getShareType() : null,
                    dartSharesOutstanding,
                    kisSharesOutstanding
            );
            return saved;
        }));
    }

    public Mono<MarketSnapshotDto> getLatestDto(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return Mono.justOrEmpty((MarketSnapshotDto) null);
        }

        return Blocking.call(() -> {
            var snapshot = asOfDate == null
                    ? marketSnapshotRepository.findTopByStockOrderByAsOfDateDesc(stock)
                    : marketSnapshotRepository.findTopByStockAndAsOfDateLessThanEqualOrderByAsOfDateDesc(stock, asOfDate);
            return snapshot.map(this::toDto).orElse(null);
        });
    }

    public MarketSnapshotDto toDto(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        return MarketSnapshotDto.builder()
                .asOfDate(snapshot.getAsOfDate())
                .marketCap(snapshot.getMarketCap())
                .floatMarketCap(snapshot.getFloatMarketCap())
                .per(toDouble(snapshot.getPer()))
                .pbr(toDouble(snapshot.getPbr()))
                .floatRatio(toDouble(snapshot.getFloatRatio()))
                .treasuryRatio(toDouble(snapshot.getTreasuryRatio()))
                .sharesOutstanding(snapshot.getSharesOutstanding())
                .source(snapshot.getSource())
                .build();
    }

    private IssuedShares resolvePrimaryIssuedShares(Stock stock, LocalDate asOfDate) {
        if (stock == null) {
            return null;
        }
        if (stock.getShareClass() == ShareClass.PREFERRED) {
            return findIssuedShares(stock, SHARE_TYPE_PREFERRED, asOfDate);
        }
        IssuedShares issuedShares = findIssuedShares(stock, SHARE_TYPE_COMMON, asOfDate);
        if (issuedShares == null) {
            issuedShares = findIssuedShares(stock, SHARE_TYPE_TOTAL, asOfDate);
        }
        return issuedShares;
    }

    private IssuedShares findIssuedShares(Stock stock, String shareType, LocalDate asOfDate) {
        return issuedSharesRepository
                .findTopByStockAndShareTypeAndIssuedSharesTotalIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                        stock,
                        shareType,
                        asOfDate
                )
                .orElse(null);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(transactionManager);
    }

    private static BigDecimal parseNullableBigDecimal(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim().replace(",", "");
        if (trimmed.isEmpty()) {
            return null;
        }

        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toBigDecimal(Long value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private static BigDecimal firstNonNull(BigDecimal primary, BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
