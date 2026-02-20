package com.qaima.importer;

import com.qaima.common.DictionaryTermNormalizer;
import com.qaima.domain.DictionaryTerm;
import com.qaima.repository.DictionaryRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryImportService {

    private final DictionaryRepository dictionaryRepository;
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
                log.warn("Empty CSV file: {}", csvPath);
                return;
            }

            Map<String, Integer> idx = buildHeaderIndex(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;

                List<String> cols = parseCsvLine(line);

                try {
                    tt.execute(status -> {
                        importSingleRow(idx, cols);
                        dictionaryRepository.flush();
                        if (em != null) em.clear();
                        return null;
                    });
                    ok++;
                } catch (DataIntegrityViolationException e) {
                    fail++;
                    log.warn("CSV {}:{} constraint error: {}", csvPath, lineNo, rootMessage(e));
                    safeClear();
                } catch (Exception e) {
                    fail++;
                    log.error("CSV {}:{} line error: {}", csvPath, lineNo, e.getMessage(), e);
                    safeClear();
                }

                if ((ok + fail) % 200 == 0) {
                    log.info("import progress: ok={}, fail={}, lastLine={}", ok, fail, lineNo);
                }
            }

            log.info("import done: ok={}, fail={}, totalLines={}", ok, fail, lineNo);
        }
    }

    private void importSingleRow(Map<String, Integer> idx, List<String> cols) {
        String termRaw = getString(cols, idx, "term");
        String descriptionRaw = getString(cols, idx, "description");
        String sourceRaw = getString(cols, idx, "source");
        String tagRaw = getString(cols, idx, "tag");

        String term = DictionaryTermNormalizer.normalizeTerm(termRaw);
        if (term == null) throw new IllegalArgumentException("term is required");

        String description = (descriptionRaw == null) ? null : descriptionRaw.trim();
        if (description == null || description.isBlank()) throw new IllegalArgumentException("description is required");

        String source = normalizeOptional(sourceRaw);
        String tag = normalizeOptional(tagRaw);

        DictionaryTerm entity = dictionaryRepository.findById(term).orElseGet(() -> new DictionaryTerm(term));
        entity.setTerm(term);
        entity.setDescription(description);
        entity.setSource(source);
        entity.setTag(tag);
        dictionaryRepository.save(entity);
    }

    private Map<String, Integer> buildHeaderIndex(String headerLine) {
        headerLine = headerLine.replace("\uFEFF", ""); // BOM 제거
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headers.get(i) == null ? "" : headers.get(i).trim();
            map.put(key, i);
        }
        return map;
    }

    private String getString(List<String> cols, Map<String, Integer> idx, String colName) {
        Integer i = idx.get(colName);
        if (i == null || i < 0 || i >= cols.size()) return null;
        String v = cols.get(i);
        if (v == null) return null;
        String trimmed = v.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 간단한 CSV 파서(따옴표 필드 + 이스케이프된 따옴표 지원)입니다.
     * 설명(description)에 쉼표가 있어도 파싱이 깨지지 않도록 합니다.
     */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        sb.append('\"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '\"') {
                    inQuotes = true;
                } else if (c == ',') {
                    out.add(sb.toString());
                    sb.setLength(0);
                } else {
                    sb.append(c);
                }
            }
        }
        out.add(sb.toString());
        return out;
    }

    private static String normalizeOptional(String v) {
        if (v == null) return null;
        String trimmed = v.trim();
        return trimmed.isBlank() ? null : trimmed;
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
