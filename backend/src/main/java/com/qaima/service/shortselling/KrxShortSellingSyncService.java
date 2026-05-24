package com.qaima.service.shortselling;

import com.qaima.common.Blocking;
import com.qaima.domain.Stock;
import com.qaima.dto.krx.KrxShortSellingMarket;
import com.qaima.dto.krx.KrxShortSellingRow;
import com.qaima.external.KrxShortSellingClient;
import com.qaima.external.KrxShortSellingClient.KrxShortSellingClientException;
import com.qaima.repository.StockRepository;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrxShortSellingSyncService {

    public static final String SOURCE = "KRX";
    public static final String SOURCE_SCREEN_ID = "MDCSTAT301";

    private static final int MAX_BACKFILL_DAYS = 3000;
    private static final int BATCH_SIZE = 1000;
    private static final int UNKNOWN_STOCK_SAMPLE_SIZE = 20;

    private static final String UPSERT_SQL = """
            INSERT INTO short_selling (
                stock_id,
                report_date,
                market_code,
                security_type,
                short_volume_total,
                short_volume_uptick_applied,
                short_volume_uptick_exempt,
                total_volume,
                short_volume_ratio,
                short_amount_total,
                short_amount_uptick_applied,
                short_amount_uptick_exempt,
                total_amount,
                short_amount_ratio,
                source,
                source_screen_id,
                created_at,
                updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                market_code = VALUES(market_code),
                security_type = VALUES(security_type),
                short_volume_total = VALUES(short_volume_total),
                short_volume_uptick_applied = VALUES(short_volume_uptick_applied),
                short_volume_uptick_exempt = VALUES(short_volume_uptick_exempt),
                total_volume = VALUES(total_volume),
                short_volume_ratio = VALUES(short_volume_ratio),
                short_amount_total = VALUES(short_amount_total),
                short_amount_uptick_applied = VALUES(short_amount_uptick_applied),
                short_amount_uptick_exempt = VALUES(short_amount_uptick_exempt),
                total_amount = VALUES(total_amount),
                short_amount_ratio = VALUES(short_amount_ratio),
                source = VALUES(source),
                source_screen_id = VALUES(source_screen_id),
                updated_at = VALUES(updated_at)
            """;

    private final KrxShortSellingClient client;
    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public Mono<KrxShortSellingDailySyncResult> syncDaily(LocalDate date) {
        LocalDate tradeDate = requireDate(date, "date");
        log.info("[KRX] short selling daily sync start. date={}", tradeDate);
        return Flux.fromArray(KrxShortSellingMarket.values())
                .concatMap(market -> client.fetchDaily(tradeDate, market))
                .collectList()
                .flatMap(marketRows -> Blocking.call(() -> upsertRows(tradeDate, marketRows)));
    }

    public Mono<KrxShortSellingProbeResult> probeDaily(LocalDate date) {
        LocalDate tradeDate = requireDate(date, "date");
        log.info("[KRX] short selling probe start. date={}", tradeDate);
        return Flux.fromArray(KrxShortSellingMarket.values())
                .concatMap(market -> client.fetchDaily(tradeDate, market)
                        .map(rows -> new KrxShortSellingMarketProbeResult(
                                market.exchangeCode(),
                                true,
                                rows.size(),
                                null,
                                null
                        ))
                        .onErrorResume(error -> {
                            String errorCode = clientErrorCode(error);
                            log.warn("[KRX] short selling probe failed. date={}, market={}, errorCode={}, message={}",
                                    tradeDate, market.exchangeCode(), errorCode, error.getMessage());
                            return Mono.just(new KrxShortSellingMarketProbeResult(
                                    market.exchangeCode(),
                                    false,
                                    0,
                                    errorCode,
                                    error.getMessage()
                            ));
                        }))
                .collectList()
                .map(results -> {
                    int fetchedCount = results.stream()
                            .mapToInt(KrxShortSellingMarketProbeResult::fetchedCount)
                            .sum();
                    int failedMarketCount = (int) results.stream()
                            .filter(result -> !result.success())
                            .count();
                    log.info("[KRX] short selling probe complete. date={}, fetched={}, failedMarkets={}",
                            tradeDate, fetchedCount, failedMarketCount);
                    return new KrxShortSellingProbeResult(
                            tradeDate,
                            fetchedCount,
                            failedMarketCount,
                            results
                    );
                });
    }

    public Mono<KrxShortSellingBackfillResult> backfill(LocalDate from, LocalDate to) {
        LocalDate start = requireDate(from, "from");
        LocalDate end = requireDate(to, "to");
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_BACKFILL_DAYS) {
            return Mono.error(new IllegalArgumentException("KRX backfill range is too large: " + days));
        }

        log.info("[KRX] short selling backfill start. from={}, to={}, days={}", start, end, days);
        return Flux.range(0, (int) days)
                .concatMap(offset -> syncDaily(start.plusDays(offset)))
                .collectList()
                .map(results -> aggregate(start, end, results));
    }

    private KrxShortSellingDailySyncResult upsertRows(
            LocalDate tradeDate,
            List<List<KrxShortSellingRow>> marketRows
    ) {
        List<KrxShortSellingRow> rows = marketRows == null
                ? List.of()
                : marketRows.stream().flatMap(List::stream).toList();
        Map<String, Long> stockIdByExchangeAndCode = loadKrxStockLookup();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int upserted = 0;
        int skippedUnknownStock = 0;
        Map<String, Integer> unknownStockCounts = new HashMap<>();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        for (KrxShortSellingRow row : rows) {
            String stockKey = stockKey(row.marketCode(), row.stockCode());
            Long stockId = stockIdByExchangeAndCode.get(stockKey);
            if (stockId == null) {
                skippedUnknownStock++;
                unknownStockCounts.merge(stockKey, 1, Integer::sum);
                continue;
            }

            batch.add(toSqlParams(stockId, row));
            if (batch.size() >= BATCH_SIZE) {
                upserted += flushBatch(tx, batch);
            }
        }

        if (!batch.isEmpty()) {
            upserted += flushBatch(tx, batch);
        }

        List<String> unknownStockSamples = unknownStockSamples(unknownStockCounts);
        if (!unknownStockSamples.isEmpty()) {
            log.warn("[KRX] short selling skipped unknown stocks. date={}, unknown={}, samples={}",
                    tradeDate, skippedUnknownStock, unknownStockSamples);
        }
        log.info("[KRX] short selling daily sync complete. date={}, fetched={}, upserted={}, unknown={}",
                tradeDate, rows.size(), upserted, skippedUnknownStock);
        return new KrxShortSellingDailySyncResult(
                tradeDate,
                rows.size(),
                upserted,
                skippedUnknownStock,
                unknownStockSamples
        );
    }

    private Map<String, Long> loadKrxStockLookup() {
        Map<String, Long> stocks = new HashMap<>();
        for (KrxShortSellingMarket market : KrxShortSellingMarket.values()) {
            for (Stock stock : stockRepository.findByExchange_CodeIgnoreCaseOrderByStockCodeAsc(market.exchangeCode())) {
                if (stock.getStockId() == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
                    continue;
                }
                stocks.put(stockKey(market.exchangeCode(), stock.getStockCode()), stock.getStockId());
            }
        }
        return stocks;
    }

    private Object[] toSqlParams(Long stockId, KrxShortSellingRow row) {
        LocalDateTime now = LocalDateTime.now();
        return new Object[] {
                stockId,
                row.tradeDate(),
                row.marketCode(),
                row.securityType(),
                decimal(row.shortVolumeTotal()),
                decimal(row.shortVolumeUptickApplied()),
                decimal(row.shortVolumeUptickExempt()),
                decimal(row.totalVolume()),
                decimal(row.shortVolumeRatio()),
                decimal(row.shortAmountTotal()),
                decimal(row.shortAmountUptickApplied()),
                decimal(row.shortAmountUptickExempt()),
                decimal(row.totalAmount()),
                decimal(row.shortAmountRatio()),
                SOURCE,
                SOURCE_SCREEN_ID,
                now,
                now
        };
    }

    private int flushBatch(TransactionTemplate tx, List<Object[]> batch) {
        int batchSize = batch.size();
        tx.executeWithoutResult(status -> jdbcTemplate.batchUpdate(UPSERT_SQL, batch));
        batch.clear();
        return batchSize;
    }

    private KrxShortSellingBackfillResult aggregate(
            LocalDate from,
            LocalDate to,
            List<KrxShortSellingDailySyncResult> results
    ) {
        List<KrxShortSellingDailySyncResult> safeResults = results == null ? List.of() : results;
        return new KrxShortSellingBackfillResult(
                from,
                to,
                safeResults.size(),
                (int) safeResults.stream().filter(result -> result.fetchedCount() > 0).count(),
                safeResults.stream().mapToInt(KrxShortSellingDailySyncResult::fetchedCount).sum(),
                safeResults.stream().mapToInt(KrxShortSellingDailySyncResult::upsertedCount).sum(),
                safeResults.stream().mapToInt(KrxShortSellingDailySyncResult::skippedUnknownStockCount).sum(),
                safeResults.stream()
                        .flatMap(result -> result.unknownStockSamples().stream())
                        .distinct()
                        .limit(UNKNOWN_STOCK_SAMPLE_SIZE)
                        .toList()
        );
    }

    private List<String> unknownStockSamples(Map<String, Integer> unknownStockCounts) {
        if (unknownStockCounts == null || unknownStockCounts.isEmpty()) {
            return List.of();
        }
        return unknownStockCounts.entrySet().stream()
                .sorted((left, right) -> {
                    int countComparison = Integer.compare(right.getValue(), left.getValue());
                    if (countComparison != 0) {
                        return countComparison;
                    }
                    return left.getKey().compareTo(right.getKey());
                })
                .limit(UNKNOWN_STOCK_SAMPLE_SIZE)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    private String clientErrorCode(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof KrxShortSellingClientException clientException) {
                return clientException.code();
            }
            current = current.getCause();
        }
        return error == null ? "UNKNOWN" : error.getClass().getSimpleName();
    }

    private String stockKey(String exchangeCode, String stockCode) {
        return normalize(exchangeCode) + ":" + normalize(stockCode);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Object decimal(BigDecimal value) {
        return value == null ? new SqlParameterValue(Types.DECIMAL, null) : value;
    }

    private LocalDate requireDate(LocalDate value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    public record KrxShortSellingDailySyncResult(
            LocalDate tradeDate,
            int fetchedCount,
            int upsertedCount,
            int skippedUnknownStockCount,
            List<String> unknownStockSamples
    ) {
    }

    public record KrxShortSellingBackfillResult(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int requestedDays,
            int dataDays,
            int fetchedCount,
            int upsertedCount,
            int skippedUnknownStockCount,
            List<String> unknownStockSamples
    ) {
    }

    public record KrxShortSellingProbeResult(
            LocalDate tradeDate,
            int fetchedCount,
            int failedMarketCount,
            List<KrxShortSellingMarketProbeResult> markets
    ) {
    }

    public record KrxShortSellingMarketProbeResult(
            String marketCode,
            boolean success,
            int fetchedCount,
            String errorCode,
            String errorMessage
    ) {
    }
}
