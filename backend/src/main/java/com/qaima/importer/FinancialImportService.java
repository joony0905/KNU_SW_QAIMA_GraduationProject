package com.qaima.importer;

import com.qaima.domain.Financial;
import com.qaima.domain.PeriodType;
import com.qaima.domain.Stock;
import com.qaima.repository.FinancialRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 파이썬이 생성한 재무제표 CSV를 읽어서 Financial 엔티티로 저장
     */
    @Transactional
    public void importFromCsv(Path csvPath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("빈 CSV 파일입니다: {}", csvPath);
                return;
            }
            Map<String, Integer> idx = buildHeaderIndex(headerLine);

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;

                String[] cols = line.split(",", -1);

                try {
                    importSingleRow(idx, cols);
                } catch (Exception e) {
                    log.error("CSV {}:{} 라인 처리 중 오류: {}", csvPath, lineNo, e.getMessage(), e);
                }

                long count = financialRepository.count();
                log.info("현재 financial 행 수 = {}", count);

            }
        }
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        String[] headers = headerLine.split(",", -1);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim(), i);
        }
        return map;
    }

    private void importSingleRow(Map<String, Integer> idx, String[] cols) {
        String stockCode = getString(cols, idx, "stock_code");
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stock_code 가 비어 있습니다.");
        }

        Stock stock = stockRepository.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalArgumentException("stock 테이블에 없는 stock_code: " + stockCode));

        Financial f = new Financial();

        // FK
        f.setStock(stock);

        // NOT NULL 필드들
        f.setReportDate(parseLocalDate(getString(cols, idx, "report_date")));
        // version은 엔티티에서 기본값 1이지만, CSV에 있으면 따라감
        Integer version = parseInteger(getString(cols, idx, "version"));
        if (version != null) {
            f.setVersion(version);
        }

        f.setFiscalYear(requiredInt(cols, idx, "fiscal_year"));
        f.setFiscalQuarter(parseInteger(getString(cols, idx, "fiscal_quarter")));
        f.setPeriodType(parsePeriodType(getString(cols, idx, "period_type")));

        // 선택 필드들
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
        f.setMarketCap(parseBigDecimal(getString(cols, idx, "market_cap")));

        f.setOperatingMargin(parseBigDecimal(getString(cols, idx, "operating_margin")));
        f.setNetMargin(parseBigDecimal(getString(cols, idx, "net_margin")));
        f.setRoe(parseBigDecimal(getString(cols, idx, "roe")));
        f.setPer(parseBigDecimal(getString(cols, idx, "per")));
        f.setPbr(parseBigDecimal(getString(cols, idx, "pbr")));

        // createdAt / updatedAt 은 @CreationTimestamp / @UpdateTimestamp 로 자동 세팅
        financialRepository.save(f);
    }


    private String getString(String[] cols, Map<String, Integer> idx, String colName) {
        Integer i = idx.get(colName);
        if (i == null || i < 0 || i >= cols.length) return null;
        String v = cols[i].trim();
        return v.isEmpty() ? null : v;
    }

    private Integer requiredInt(String[] cols, Map<String, Integer> idx, String colName) {
        String v = getString(cols, idx, colName);
        if (v == null) {
            throw new IllegalArgumentException(colName + " 는 필수 정수값입니다.");
        }
        return Integer.parseInt(v);
    }

    private Integer parseInteger(String v) {
        if (v == null) return null;
        return v.isBlank() ? null : Integer.parseInt(v);
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
        if (v == null || v.isBlank()) {
            throw new IllegalArgumentException("period_type 이 비어 있습니다.");
        }
        try {
            return PeriodType.valueOf(v);
        } catch (IllegalArgumentException ex) {
            switch (v) {
                case "A":
                    return PeriodType.A;
                default:
                    throw new IllegalArgumentException("지원하지 않는 period_type: " + v);
            }
        }
    }
}


/** 사용법
 * csv를 파일 경로에 맞춰서 넣고 터미널에서도 똑같이 경로를 맞춘 후 아래 2줄의 코드를 터미널에서 실행
 * SPRING_PROFILES_ACTIVE=import-financial-csv \
 * ./gradlew bootRun --args='data/financial_005930_2019_2023.csv'
 */