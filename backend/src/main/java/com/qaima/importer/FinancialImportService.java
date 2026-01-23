package com.qaima.importer;

import com.qaima.domain.Exchange;
import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.repository.ExchangeRepository;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialImportService {

    private final StockRepository stockRepository;
    private final FinancialRepository financialRepository;
    private final ExchangeRepository exchangeRepository;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;


    public void importFromCsv(Path csvPath) throws IOException {
        TransactionTemplate tt = new TransactionTemplate(transactionManager);
        tt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        int lineNo = 0;
        int ok = 0;
        int fail = 0;

        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {

            String headerLine = reader.readLine();
            lineNo++;
            if (headerLine == null) {
                log.warn("빈 CSV 파일입니다: {}", csvPath);
                return;
            }
            Map<String, Integer> idx = buildHeaderIndex(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);

                try {
                    tt.execute(status -> {
                        importSingleRow(idx, cols);
                        financialRepository.flush();
                        em.clear();
                        return null;
                    });
                    ok++;
                } catch (DataIntegrityViolationException e) {
                    fail++;
                    log.warn("CSV {}:{} 저장 스킵 (DB 제약 위반): {}", csvPath, lineNo, rootMessage(e));
                    safeClear();
                } catch (Exception e) {
                    fail++;
                    log.error("CSV {}:{} 라인 처리 중 오류: {}", csvPath, lineNo, e.getMessage(), e);
                    safeClear();
                }

                if ((ok + fail) % 200 == 0) {
                    log.info("import progress: ok={}, fail={}, lastLine={}", ok, fail, lineNo);
                }
            }

            log.info("import done: ok={}, fail={}, totalLines={}", ok, fail, lineNo);

        }
    }

    private void importSingleRow(Map<String, Integer> idx, String[] cols) {
        String stockCode = getString(cols, idx, "stock_code");
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stock_code 가 비어 있습니다.");
        }

        String stockName = getString(cols, idx, "name");

        Stock stock = stockRepository.findByExchangeCodeAndStockCodeIgnoreCase("KRX", stockCode)
                .orElseGet(() -> {
                    Exchange krx = exchangeRepository.findByCode("KRX")
                            .orElseThrow(() -> new IllegalStateException("exchange 테이블에 code=KRX가 없습니다."));

                    Stock s = new Stock();
                    s.setStockCode(stockCode);
                    if (stockName != null && !stockName.isBlank()) s.setCompanyName(stockName);
                    s.setExchange(krx);
                    log.warn("stock 자동 생성: {} ({})", stockCode, stockName);
                    return stockRepository.save(s);
                });

        Financial f = new Financial();
        f.setStock(stock);

        // NOT NULL
        LocalDate reportDate = parseLocalDate(getString(cols, idx, "report_date"));
        if (reportDate == null) throw new IllegalArgumentException("report_date 가 비어 있습니다.");
        f.setReportDate(reportDate);

        Integer version = parseInteger(getString(cols, idx, "version"));
        if (version != null) f.setVersion(version);

        f.setFiscalYear(requiredInt(cols, idx, "fiscal_year"));
        f.setPeriodType(parsePeriodType(getString(cols, idx, "period_type")));

        // CSV period_no 반영 (반기 H2 유지/유니크 충돌 방지)
        Integer periodNo = parseInteger(getString(cols, idx, "period_no"));
        if (periodNo != null) f.setPeriodNo(periodNo);

        f.setFiscalQuarter(parseInteger(getString(cols, idx, "fiscal_quarter")));
        f.setFilingDate(parseLocalDate(getString(cols, idx, "filing_date")));
        f.setCurrency(getString(cols, idx, "currency"));
        f.setSource(getString(cols, idx, "source"));

        f.setRevenue(parseBigDecimal(getString(cols, idx, "revenue")));
        f.setGrossProfit(parseBigDecimal(getString(cols, idx, "gross_profit")));
        f.setOperatingIncome(parseBigDecimal(getString(cols, idx, "operating_income")));
        f.setNetIncome(parseBigDecimal(getString(cols, idx, "net_income")));
        f.setAssets(parseBigDecimal(getString(cols, idx, "assets")));
        f.setLiabilities(parseBigDecimal(getString(cols, idx, "liabilities")));
        f.setEquity(parseBigDecimal(getString(cols, idx, "equity")));
        f.setCapitalStock(parseBigDecimal(getString(cols, idx, "capital_stock")));
        f.setRetainedEarnings(parseBigDecimal(getString(cols, idx, "retained_earnings")));
        f.setCashAndEquivalents(parseBigDecimal(getString(cols, idx, "cash_and_equivalents")));

        financialRepository.save(f);
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        headerLine = headerLine.replace("\uFEFF", ""); // BOM 제거
        String[] headers = headerLine.split(",", -1);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim(), i);
        }
        return map;
    }

    private String getString(String[] cols, Map<String, Integer> idx, String colName) {
        Integer i = idx.get(colName);
        if (i == null || i < 0 || i >= cols.length) return null;
        String v = cols[i].trim();
        return v.isEmpty() ? null : v;
    }

    private Integer requiredInt(String[] cols, Map<String, Integer> idx, String colName) {
        String v = getString(cols, idx, colName);
        if (v == null) throw new IllegalArgumentException(colName + " 는 필수 정수값입니다.");
        return Integer.parseInt(v);
    }

    private Integer parseInteger(String v) {
        if (v == null || v.isBlank()) return null;
        String s = v.trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            // "1.0" 같은 케이스 처리
            try {
                double d = Double.parseDouble(s);
                return (int) d;
            } catch (NumberFormatException ex) {
                throw e;
            }
        }
    }


    private LocalDate parseLocalDate(String v) {
        if (v == null || v.isBlank()) return null;
        return LocalDate.parse(v);
    }

    private BigDecimal parseBigDecimal(String v) {
        if (v == null || v.isBlank()) return null;
        return new BigDecimal(v);
    }

    private PeriodType parsePeriodType(String v) {
        if (v == null || v.isBlank()) throw new IllegalArgumentException("period_type 이 비어 있습니다.");

        String code = v.trim().toUpperCase();
        try {
            return PeriodType.valueOf(code);
        } catch (IllegalArgumentException ex) {
            // 안전하게 스킵시키고 싶으면 여기서 throw (현재 구현은 throw로 스킵 처리)
            throw new IllegalArgumentException("지원하지 않는 period_type: " + code);
        }
    }

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) cur = cur.getCause();
        return cur.getMessage();
    }

    private void safeClear() {
        try {
            if (em != null) em.clear();
        } catch (Exception ignored) {}
    }
}
