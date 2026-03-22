package com.qaima.importer;

import com.qaima.domain.Stock;
import com.qaima.repository.StockRepository;
import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShortSellingImportService {

    private static final int BATCH_SIZE = 1000;

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

    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public void importFromCsv(Path csvPath) throws IOException {
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        Map<String, Long> stockIdByCode = loadStockIdByCode();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int lineNo = 0;
        int upserted = 0;
        int skippedBlank = 0;
        int skippedUnknownStock = 0;
        int failed = 0;
        Map<String, Integer> missingStockCounts = new LinkedHashMap<>();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            lineNo++;
            if (headerLine == null) {
                log.warn("Short selling CSV is empty: {}", csvPath);
                return;
            }

            Map<String, Integer> idx = buildHeaderIndex(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    skippedBlank++;
                    continue;
                }

                String[] cols = line.split(",", -1);

                try {
                    Object[] params = toSqlParams(idx, cols, stockIdByCode, missingStockCounts);
                    if (params == null) {
                        skippedUnknownStock++;
                        continue;
                    }

                    batch.add(params);
                    if (batch.size() >= BATCH_SIZE) {
                        upserted += flushBatch(tx, batch);
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("Short selling CSV row skipped. file={}, line={}, cause={}", csvPath, lineNo, e.getMessage());
                }

                if ((upserted + skippedUnknownStock + failed) % 5000 == 0 && (upserted + skippedUnknownStock + failed) > 0) {
                    log.info(
                            "short selling import progress: upserted={}, skippedUnknownStock={}, failed={}, lastLine={}",
                            upserted,
                            skippedUnknownStock,
                            failed,
                            lineNo
                    );
                }
            }
        }

        if (!batch.isEmpty()) {
            upserted += flushBatch(tx, batch);
        }

        log.info(
                "short selling import done: file={}, upserted={}, skippedBlank={}, skippedUnknownStock={}, failed={}",
                csvPath,
                upserted,
                skippedBlank,
                skippedUnknownStock,
                failed
        );

        if (!missingStockCounts.isEmpty()) {
            missingStockCounts.entrySet().stream()
                    .limit(20)
                    .forEach(entry -> log.warn("unknown stock code skipped: {} (count={})", entry.getKey(), entry.getValue()));
        }
    }

    private int flushBatch(TransactionTemplate tx, List<Object[]> batch) {
        int batchSize = batch.size();
        tx.executeWithoutResult(status -> jdbcTemplate.batchUpdate(UPSERT_SQL, batch));
        batch.clear();
        return batchSize;
    }

    private Map<String, Long> loadStockIdByCode() {
        Map<String, Long> map = new HashMap<>();
        for (Stock stock : stockRepository.findAllByOrderByStockCodeAsc()) {
            if (stock.getStockCode() == null || stock.getStockId() == null) {
                continue;
            }
            map.put(stock.getStockCode().trim().toUpperCase(), stock.getStockId());
        }
        return map;
    }

    private Object[] toSqlParams(
            Map<String, Integer> idx,
            String[] cols,
            Map<String, Long> stockIdByCode,
            Map<String, Integer> missingStockCounts
    ) {
        String stockCode = requireString(cols, idx, "stock_code");
        String normalizedCode = stockCode.toUpperCase();
        Long stockId = stockIdByCode.get(normalizedCode);
        if (stockId == null) {
            missingStockCounts.merge(normalizedCode, 1, Integer::sum);
            return null;
        }

        LocalDate reportDate = LocalDate.parse(requireString(cols, idx, "report_date"));
        String marketCode = requireString(cols, idx, "market_code");
        String securityType = requireString(cols, idx, "security_type");
        String source = defaultString(getString(cols, idx, "source"), "KRX");
        String sourceScreenId = defaultString(getString(cols, idx, "source_screen_id"), "MDCSTAT301");
        LocalDateTime now = LocalDateTime.now();

        return new Object[] {
                stockId,
                reportDate,
                marketCode,
                securityType,
                decimalValue(getString(cols, idx, "short_volume_total")),
                decimalValue(getString(cols, idx, "short_volume_uptick_applied")),
                decimalValue(getString(cols, idx, "short_volume_uptick_exempt")),
                decimalValue(getString(cols, idx, "total_volume")),
                decimalValue(getString(cols, idx, "short_volume_ratio")),
                decimalValue(getString(cols, idx, "short_amount_total")),
                decimalValue(getString(cols, idx, "short_amount_uptick_applied")),
                decimalValue(getString(cols, idx, "short_amount_uptick_exempt")),
                decimalValue(getString(cols, idx, "total_amount")),
                decimalValue(getString(cols, idx, "short_amount_ratio")),
                source,
                sourceScreenId,
                now,
                now
        };
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        String[] headers = headerLine.replace("\uFEFF", "").split(",", -1);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim(), i);
        }
        return map;
    }

    private String getString(String[] cols, Map<String, Integer> idx, String colName) {
        Integer i = idx.get(colName);
        if (i == null || i < 0 || i >= cols.length) {
            return null;
        }
        String value = cols[i].trim();
        return value.isEmpty() ? null : value;
    }

    private String requireString(String[] cols, Map<String, Integer> idx, String colName) {
        String value = getString(cols, idx, colName);
        if (value == null) {
            throw new IllegalArgumentException("Required column is blank: " + colName);
        }
        return value;
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private Object decimalValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SqlParameterValue(Types.DECIMAL, null);
        }
        return new BigDecimal(raw.trim());
    }
}
