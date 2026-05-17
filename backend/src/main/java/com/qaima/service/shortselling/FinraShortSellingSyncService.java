package com.qaima.service.shortselling;

import com.qaima.common.Blocking;
import com.qaima.domain.Stock;
import com.qaima.dto.finra.FinraShortSaleVolumeRow;
import com.qaima.external.FinraShortSaleVolumeClient;
import com.qaima.repository.StockRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
public class FinraShortSellingSyncService {

    public static final String SOURCE = "FINRA";
    public static final String SOURCE_SCREEN_ID = "CNMS";
    public static final String SECURITY_TYPE = "EQUITY";

    private static final int MAX_BACKFILL_DAYS = 3000;
    private static final int BATCH_SIZE = 1000;
    private static final String[] US_EXCHANGES = {"NASDAQ", "NYSE"};

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

    private final FinraShortSaleVolumeClient client;
    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public Mono<FinraShortSellingDailySyncResult> syncDaily(LocalDate date) {
        LocalDate tradeDate = requireDate(date, "date");
        return client.fetchConsolidatedNmsDaily(tradeDate)
                .flatMap(rows -> Blocking.call(() -> upsertRows(tradeDate, rows)));
    }

    public Mono<FinraShortSellingBackfillResult> backfill(LocalDate from, LocalDate to) {
        LocalDate start = requireDate(from, "from");
        LocalDate end = requireDate(to, "to");
        if (start.isAfter(end)) {
            return Mono.error(new IllegalArgumentException("from must be before or equal to to"));
        }

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_BACKFILL_DAYS) {
            return Mono.error(new IllegalArgumentException("FINRA backfill range is too large: " + days));
        }

        return Flux.range(0, (int) days)
                .concatMap(offset -> syncDaily(start.plusDays(offset)))
                .collectList()
                .map(results -> aggregate(start, end, results));
    }

    private FinraShortSellingDailySyncResult upsertRows(
            LocalDate tradeDate,
            List<FinraShortSaleVolumeRow> rows
    ) {
        List<FinraShortSaleVolumeRow> safeRows = rows == null ? List.of() : rows;
        UsStockLookup stocks = loadUsStockLookup();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int upserted = 0;
        int skippedUnknownStock = 0;
        int skippedAmbiguousStock = 0;
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        for (FinraShortSaleVolumeRow row : safeRows) {
            if (row == null || row.symbol() == null || row.symbol().isBlank()) {
                continue;
            }

            String symbol = normalizeSymbol(row.symbol());
            if (stocks.ambiguousSymbols().contains(symbol)) {
                skippedAmbiguousStock++;
                continue;
            }

            Long stockId = stocks.stockIdBySymbol().get(symbol);
            if (stockId == null) {
                skippedUnknownStock++;
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

        log.info("[FINRA] short selling daily sync complete. date={}, fetched={}, upserted={}, unknown={}, ambiguous={}",
                tradeDate, safeRows.size(), upserted, skippedUnknownStock, skippedAmbiguousStock);
        return new FinraShortSellingDailySyncResult(
                tradeDate,
                safeRows.size(),
                upserted,
                skippedUnknownStock,
                skippedAmbiguousStock
        );
    }

    private Object[] toSqlParams(Long stockId, FinraShortSaleVolumeRow row) {
        LocalDateTime now = LocalDateTime.now();
        BigDecimal shortVolume = row.shortVolume();
        BigDecimal shortExemptVolume = row.shortExemptVolume();
        BigDecimal totalVolume = row.totalVolume();
        return new Object[] {
                stockId,
                row.tradeDate(),
                row.market(),
                SECURITY_TYPE,
                decimal(shortVolume),
                decimal(null),
                decimal(shortExemptVolume),
                decimal(totalVolume),
                decimal(ratio(shortVolume, totalVolume)),
                decimal(null),
                decimal(null),
                decimal(null),
                decimal(null),
                decimal(null),
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

    private UsStockLookup loadUsStockLookup() {
        Map<String, Long> stockIdBySymbol = new HashMap<>();
        Set<String> ambiguousSymbols = new HashSet<>();

        for (String exchange : US_EXCHANGES) {
            for (Stock stock : stockRepository.findByExchange_CodeIgnoreCaseOrderByStockCodeAsc(exchange)) {
                if (stock.getStockId() == null || stock.getStockCode() == null || stock.getStockCode().isBlank()) {
                    continue;
                }

                String symbol = normalizeSymbol(stock.getStockCode());
                if (ambiguousSymbols.contains(symbol)) {
                    continue;
                }

                Long existing = stockIdBySymbol.putIfAbsent(symbol, stock.getStockId());
                if (existing != null && !existing.equals(stock.getStockId())) {
                    stockIdBySymbol.remove(symbol);
                    ambiguousSymbols.add(symbol);
                }
            }
        }

        return new UsStockLookup(stockIdBySymbol, ambiguousSymbols);
    }

    private FinraShortSellingBackfillResult aggregate(
            LocalDate from,
            LocalDate to,
            List<FinraShortSellingDailySyncResult> results
    ) {
        List<FinraShortSellingDailySyncResult> safeResults = results == null ? List.of() : results;
        return new FinraShortSellingBackfillResult(
                from,
                to,
                safeResults.size(),
                (int) safeResults.stream().filter(result -> result.fetchedCount() > 0).count(),
                safeResults.stream().mapToInt(FinraShortSellingDailySyncResult::fetchedCount).sum(),
                safeResults.stream().mapToInt(FinraShortSellingDailySyncResult::upsertedCount).sum(),
                safeResults.stream().mapToInt(FinraShortSellingDailySyncResult::skippedUnknownStockCount).sum(),
                safeResults.stream().mapToInt(FinraShortSellingDailySyncResult::skippedAmbiguousStockCount).sum()
        );
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
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

    private String normalizeSymbol(String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private record UsStockLookup(
            Map<String, Long> stockIdBySymbol,
            Set<String> ambiguousSymbols
    ) {
    }

    public record FinraShortSellingDailySyncResult(
            LocalDate date,
            int fetchedCount,
            int upsertedCount,
            int skippedUnknownStockCount,
            int skippedAmbiguousStockCount
    ) {
    }

    public record FinraShortSellingBackfillResult(
            LocalDate requestedFrom,
            LocalDate requestedTo,
            int requestedDays,
            int dataDays,
            int fetchedCount,
            int upsertedCount,
            int skippedUnknownStockCount,
            int skippedAmbiguousStockCount
    ) {
    }
}
