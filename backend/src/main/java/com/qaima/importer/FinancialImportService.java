package com.qaima.importer;

import com.qaima.domain.Stock;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialImportService {

    private static final int BATCH_SIZE = 1000;

    /**
     * 정책:
     * - financial은 "(stock_id, fiscal_year, period_type, period_no) 기준 최신 스냅샷 1개 유지"
     * - version은 별도 메타로 저장하지만 unique key에는 포함하지 않음
     * - 동일 기간 재적재 시 ON DUPLICATE KEY UPDATE로 최신값으로 덮어씀
     */
    private static final String UPSERT_SQL = """
            INSERT INTO financial (
                stock_id,
                report_date,
                version,
                fiscal_year,
                period_no,
                fiscal_quarter,
                period_type,
                filing_date,
                currency,
                source,

                revenue,
                gross_profit,
                operating_income,
                net_income,

                assets,
                current_assets,
                liabilities,
                current_liabilities,
                equity,

                capital_stock,
                retained_earnings,

                cash_and_equivalents,
                accounts_receivable,
                inventories,

                short_term_borrowings,
                current_portion_of_long_term_borrowings,
                long_term_borrowings,

                operating_cash_flow,
                investing_cash_flow,
                financing_cash_flow,

                interest_expense,
                capex_ppe,
                capex_intangible,
                depreciation_expense,
                amortization_expense,
                income_tax_expense,

                market_cap,

                operating_margin,
                net_margin,
                roe,
                per,
                pbr,

                created_at,
                updated_at
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?, ?, ?,
                ?,
                ?, ?, ?, ?, ?,
                ?, ?
            )
            ON DUPLICATE KEY UPDATE
                report_date = VALUES(report_date),
                version = VALUES(version),
                fiscal_quarter = VALUES(fiscal_quarter),
                filing_date = VALUES(filing_date),
                currency = VALUES(currency),
                source = VALUES(source),

                revenue = VALUES(revenue),
                gross_profit = VALUES(gross_profit),
                operating_income = VALUES(operating_income),
                net_income = VALUES(net_income),

                assets = VALUES(assets),
                current_assets = VALUES(current_assets),
                liabilities = VALUES(liabilities),
                current_liabilities = VALUES(current_liabilities),
                equity = VALUES(equity),

                capital_stock = VALUES(capital_stock),
                retained_earnings = VALUES(retained_earnings),

                cash_and_equivalents = VALUES(cash_and_equivalents),
                accounts_receivable = VALUES(accounts_receivable),
                inventories = VALUES(inventories),

                short_term_borrowings = VALUES(short_term_borrowings),
                current_portion_of_long_term_borrowings = VALUES(current_portion_of_long_term_borrowings),
                long_term_borrowings = VALUES(long_term_borrowings),

                operating_cash_flow = VALUES(operating_cash_flow),
                investing_cash_flow = VALUES(investing_cash_flow),
                financing_cash_flow = VALUES(financing_cash_flow),

                interest_expense = VALUES(interest_expense),
                capex_ppe = VALUES(capex_ppe),
                capex_intangible = VALUES(capex_intangible),
                depreciation_expense = VALUES(depreciation_expense),
                amortization_expense = VALUES(amortization_expense),
                income_tax_expense = VALUES(income_tax_expense),

                market_cap = VALUES(market_cap),

                operating_margin = VALUES(operating_margin),
                net_margin = VALUES(net_margin),
                roe = VALUES(roe),
                per = VALUES(per),
                pbr = VALUES(pbr),

                updated_at = VALUES(updated_at)
            """;

    private final StockRepository stockRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    public ImportResult importFromCsv(Path csvPath) throws IOException {
        return importFromCsv(csvPath, null);
    }

    public ImportResult importFromCsv(Path csvPath, String defaultExchangeCode) throws IOException {
        if (!Files.exists(csvPath)) {
            throw new IllegalArgumentException("CSV file does not exist: " + csvPath);
        }

        StockLookup stockLookup = loadStockLookup();
        String normalizedDefaultExchange = normalizeExchangeCode(defaultExchangeCode);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int lineNo = 0;
        int upserted = 0;
        int skippedBlank = 0;
        int skippedUnknownStock = 0;
        int skippedAmbiguousStock = 0;
        int failed = 0;

        Map<String, Integer> missingStockCounts = new LinkedHashMap<>();
        Map<String, Integer> ambiguousStockCounts = new LinkedHashMap<>();
        List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            lineNo++;
            if (headerLine == null) {
                log.warn("Financial CSV is empty: {}", csvPath);
                return new ImportResult(csvPath.toString(), normalizedDefaultExchange, 0, 0, 0, 0, 0);
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
                    RowParams rowParams = toSqlParams(
                            idx,
                            cols,
                            stockLookup,
                            normalizedDefaultExchange,
                            missingStockCounts,
                            ambiguousStockCounts
                    );
                    if (rowParams == RowParams.UNKNOWN_STOCK) {
                        skippedUnknownStock++;
                        continue;
                    }
                    if (rowParams == RowParams.AMBIGUOUS_STOCK) {
                        skippedAmbiguousStock++;
                        continue;
                    }
                    Object[] params = rowParams.params();
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
                    log.warn(
                            "Financial CSV row skipped. file={}, line={}, cause={}",
                            csvPath,
                            lineNo,
                            e.getMessage()
                    );
                }

                int processedRows = lineNo - 1;
                if (processedRows > 0 && processedRows % 5000 == 0) {
                    log.info(
                            "financial import progress: processedRows={}, upserted={}, skippedUnknownStock={}, skippedAmbiguousStock={}, failed={}, lastLine={}",
                            processedRows,
                            upserted,
                            skippedUnknownStock,
                            skippedAmbiguousStock,
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
                "financial import done: file={}, upserted={}, skippedBlank={}, skippedUnknownStock={}, skippedAmbiguousStock={}, failed={}",
                csvPath,
                upserted,
                skippedBlank,
                skippedUnknownStock,
                skippedAmbiguousStock,
                failed
        );

        if (!missingStockCounts.isEmpty()) {
            missingStockCounts.entrySet().stream()
                    .limit(20)
                    .forEach(entry ->
                            log.warn("unknown stock code skipped: {} (count={})", entry.getKey(), entry.getValue()));
        }

        if (!ambiguousStockCounts.isEmpty()) {
            ambiguousStockCounts.entrySet().stream()
                    .limit(20)
                    .forEach(entry ->
                            log.warn("ambiguous stock code skipped: {} (count={})", entry.getKey(), entry.getValue()));
        }

        return new ImportResult(
                csvPath.toString(),
                normalizedDefaultExchange,
                upserted,
                skippedBlank,
                skippedUnknownStock,
                skippedAmbiguousStock,
                failed
        );
    }

    private int flushBatch(TransactionTemplate tx, List<Object[]> batch) {
        int batchSize = batch.size();
        tx.executeWithoutResult(status -> jdbcTemplate.batchUpdate(UPSERT_SQL, batch));
        batch.clear();
        return batchSize;
    }

    private StockLookup loadStockLookup() {
        Map<String, Long> byListing = new HashMap<>();
        Map<String, Long> byCode = new HashMap<>();
        Set<String> ambiguousCodes = new HashSet<>();

        for (Stock stock : stockRepository.findAllByOrderByStockCodeAsc()) {
            if (stock.getStockId() == null || stock.getStockCode() == null || stock.getExchange() == null) {
                continue;
            }

            String stockCode = normalizeStockCode(stock.getStockCode());
            String exchangeCode = normalizeExchangeCode(stock.getExchange().getCode());
            byListing.put(listingKey(exchangeCode, stockCode), stock.getStockId());

            if (ambiguousCodes.contains(stockCode)) {
                continue;
            }

            Long existing = byCode.putIfAbsent(stockCode, stock.getStockId());
            if (existing != null && !existing.equals(stock.getStockId())) {
                byCode.remove(stockCode);
                ambiguousCodes.add(stockCode);
            }
        }
        return new StockLookup(byListing, byCode, ambiguousCodes);
    }

    private RowParams toSqlParams(
            Map<String, Integer> idx,
            String[] cols,
            StockLookup stockLookup,
            String defaultExchangeCode,
            Map<String, Integer> missingStockCounts,
            Map<String, Integer> ambiguousStockCounts
    ) {
        String stockCode = requireString(cols, idx, "stock_code");
        String normalizedCode = normalizeStockCode(stockCode);
        String exchangeCode = normalizeExchangeCode(firstNonBlank(getString(cols, idx, "exchange_code"), defaultExchangeCode));

        Long stockId;
        if (exchangeCode == null) {
            if (stockLookup.ambiguousCodes().contains(normalizedCode)) {
                ambiguousStockCounts.merge(normalizedCode, 1, Integer::sum);
                return RowParams.AMBIGUOUS_STOCK;
            }
            stockId = stockLookup.byCode().get(normalizedCode);
        } else {
            stockId = stockLookup.byListing().get(listingKey(exchangeCode, normalizedCode));
        }

        if (stockId == null) {
            String missingKey = exchangeCode == null ? normalizedCode : listingKey(exchangeCode, normalizedCode);
            missingStockCounts.merge(missingKey, 1, Integer::sum);
            return RowParams.UNKNOWN_STOCK;
        }

        LocalDate reportDate = parseLocalDate(requireString(cols, idx, "report_date"));
        Integer version = requiredInteger(cols, idx, "version");
        Integer fiscalYear = requiredInteger(cols, idx, "fiscal_year");
        String periodType = normalizePeriodType(requireString(cols, idx, "period_type"));
        Integer periodNo = normalizePeriodNo(periodType, parseInteger(getString(cols, idx, "period_no")));

        Integer fiscalQuarter = parseInteger(getString(cols, idx, "fiscal_quarter"));
        LocalDate filingDate = parseLocalDate(getString(cols, idx, "filing_date"));
        String currency = trimToNull(getString(cols, idx, "currency"));
        String source = trimToNull(getString(cols, idx, "source"));

        LocalDateTime now = LocalDateTime.now();

        return new RowParams(new Object[]{
                stockId,
                toSqlDate(reportDate),
                version,
                fiscalYear,
                periodNo,
                intOrNull(fiscalQuarter),
                periodType,
                toSqlDateOrNull(filingDate),
                varcharOrNull(currency),
                varcharOrNull(source),

                decimalValue(getString(cols, idx, "revenue")),
                decimalValue(getString(cols, idx, "gross_profit")),
                decimalValue(getString(cols, idx, "operating_income")),
                decimalValue(getString(cols, idx, "net_income")),

                decimalValue(getString(cols, idx, "assets")),
                decimalValue(getString(cols, idx, "current_assets")),
                decimalValue(getString(cols, idx, "liabilities")),
                decimalValue(getString(cols, idx, "current_liabilities")),
                decimalValue(getString(cols, idx, "equity")),

                decimalValue(getString(cols, idx, "capital_stock")),
                decimalValue(getString(cols, idx, "retained_earnings")),

                decimalValue(getString(cols, idx, "cash_and_equivalents")),
                decimalValue(getString(cols, idx, "accounts_receivable")),
                decimalValue(getString(cols, idx, "inventories")),

                decimalValue(getString(cols, idx, "short_term_borrowings")),
                decimalValue(getString(cols, idx, "current_portion_of_long_term_borrowings")),
                decimalValue(getString(cols, idx, "long_term_borrowings")),

                decimalValue(getString(cols, idx, "operating_cash_flow")),
                decimalValue(getString(cols, idx, "investing_cash_flow")),
                decimalValue(getString(cols, idx, "financing_cash_flow")),

                decimalValue(getString(cols, idx, "interest_expense")),
                decimalValue(getString(cols, idx, "capex_ppe")),
                decimalValue(getString(cols, idx, "capex_intangible")),
                decimalValue(getString(cols, idx, "depreciation_expense")),
                decimalValue(getString(cols, idx, "amortization_expense")),
                decimalValue(getString(cols, idx, "income_tax_expense")),

                decimalValue(getString(cols, idx, "market_cap")),

                decimalValue(getString(cols, idx, "operating_margin")),
                decimalValue(getString(cols, idx, "net_margin")),
                decimalValue(getString(cols, idx, "roe")),
                decimalValue(getString(cols, idx, "per")),
                decimalValue(getString(cols, idx, "pbr")),

                Timestamp.valueOf(now),
                Timestamp.valueOf(now)
        });
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
        String value = cols[i];
        if (value == null) {
            return null;
        }
        value = value.trim();
        return value.isEmpty() ? null : value;
    }

    private String requireString(String[] cols, Map<String, Integer> idx, String colName) {
        String value = getString(cols, idx, colName);
        if (value == null) {
            throw new IllegalArgumentException("Required column is blank: " + colName);
        }
        return value;
    }

    private Integer requiredInteger(String[] cols, Map<String, Integer> idx, String colName) {
        Integer value = parseInteger(requireString(cols, idx, colName));
        if (value == null) {
            throw new IllegalArgumentException("Required integer column is blank: " + colName);
        }
        return value;
    }

    private String normalizeStockCode(String stockCode) {
        String trimmed = stockCode.trim().toUpperCase();

        if (trimmed.matches("\\d+")) {
            return String.format("%06d", Integer.parseInt(trimmed));
        }
        return trimmed;
    }

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return null;
        }

        String normalized = exchangeCode.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return switch (normalized) {
            case "XKRX", "KRX", "KOSPI" -> "KOSPI";
            case "XKOS", "KOSDAQ" -> "KOSDAQ";
            case "XKON", "KONEX" -> "KONEX";
            case "XNYS", "NYSE" -> "NYSE";
            case "XNAS", "NASDAQ" -> "NASDAQ";
            default -> normalized;
        };
    }

    private String listingKey(String exchangeCode, String stockCode) {
        return exchangeCode + ":" + stockCode;
    }

    private String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        if (right != null && !right.isBlank()) {
            return right;
        }
        return null;
    }

    private String normalizePeriodType(String raw) {
        String code = raw.trim().toUpperCase();
        return switch (code) {
            case "A", "Q", "H", "TTM" -> code;
            default -> throw new IllegalArgumentException("Unsupported period_type: " + code);
        };
    }

    private Integer normalizePeriodNo(String periodType, Integer rawPeriodNo) {
        return switch (periodType) {
            case "A" -> 1;
            case "TTM" -> 0;
            case "Q" -> {
                if (rawPeriodNo == null || rawPeriodNo < 1 || rawPeriodNo > 4) {
                    throw new IllegalArgumentException("Q period_no must be 1~4");
                }
                yield rawPeriodNo;
            }
            case "H" -> {
                if (rawPeriodNo == null || rawPeriodNo < 1 || rawPeriodNo > 2) {
                    throw new IllegalArgumentException("H period_no must be 1~2");
                }
                yield rawPeriodNo;
            }
            default -> throw new IllegalArgumentException("Unsupported period_type: " + periodType);
        };
    }

    private Integer parseInteger(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }

        String s = v.trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            try {
                double d = Double.parseDouble(s);
                return (int) d;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid integer value: " + v, ex);
            }
        }
    }

    private LocalDate parseLocalDate(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return LocalDate.parse(v.trim());
    }

    private Object decimalValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return new SqlParameterValue(Types.DECIMAL, null);
        }
        return new BigDecimal(raw.trim());
    }

    private Object intOrNull(Integer value) {
        if (value == null) {
            return new SqlParameterValue(Types.INTEGER, null);
        }
        return value;
    }

    private Object varcharOrNull(String value) {
        if (value == null || value.isBlank()) {
            return new SqlParameterValue(Types.VARCHAR, null);
        }
        return value.trim();
    }

    private Date toSqlDate(LocalDate value) {
        return Date.valueOf(value);
    }

    private Object toSqlDateOrNull(LocalDate value) {
        if (value == null) {
            return new SqlParameterValue(Types.DATE, null);
        }
        return Date.valueOf(value);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record StockLookup(
            Map<String, Long> byListing,
            Map<String, Long> byCode,
            Set<String> ambiguousCodes
    ) {
    }

    private record RowParams(Object[] params) {
        private static final RowParams UNKNOWN_STOCK = new RowParams(null);
        private static final RowParams AMBIGUOUS_STOCK = new RowParams(null);
    }

    public record ImportResult(
            String csvPath,
            String defaultExchangeCode,
            int upserted,
            int skippedBlank,
            int skippedUnknownStock,
            int skippedAmbiguousStock,
            int failed
    ) {
    }
}
