package com.qaima.importer;

import com.qaima.domain.Stock;
import com.qaima.domain.StockAlias;
import com.qaima.repository.StockAliasRepository;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAliasImportService {

    private final StockRepository stockRepository;
    private final StockAliasRepository stockAliasRepository;
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
                        stockAliasRepository.flush();
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
        String exchangeCode = getString(cols, idx, "exchange_code");
        String stockCode = getString(cols, idx, "stock_code");
        String aliasName = getString(cols, idx, "alias_name");

        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stock_code 가 비어 있습니다.");
        }
        if (aliasName == null || aliasName.isBlank()) {
            throw new IllegalArgumentException("alias_name 가 비어 있습니다.");
        }

        String normalizedExchange = normalizeExchangeCode(exchangeCode);
        if (normalizedExchange == null || normalizedExchange.isBlank()) {
            throw new IllegalArgumentException("exchange_code 가 비어 있습니다.");
        }

        Stock stock = stockRepository
                .findByExchangeCodeAndStockCodeIgnoreCase(normalizedExchange, stockCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown stock: " + normalizedExchange + ":" + stockCode
                ));

        StockAlias alias = new StockAlias();
        alias.setStock(stock);
        alias.setAliasName(aliasName.trim());
        stockAliasRepository.save(alias);
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        headerLine = headerLine.replace("\uFEFF", "");
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

    private String normalizeExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;
        String trimmed = exchangeCode.trim();
        if (trimmed.isBlank()) return null;

        return switch (trimmed.toUpperCase()) {
            case "XKRX", "KRX" -> "KOSPI";
            case "XKOS" -> "KOSDAQ";
            case "XNYS" -> "NYSE";
            case "XNAS" -> "NASDAQ";
            default -> trimmed.toUpperCase();
        };
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
