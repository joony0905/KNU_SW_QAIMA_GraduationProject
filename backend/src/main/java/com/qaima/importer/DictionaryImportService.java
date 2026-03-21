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
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

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

            Map<String, Integer> indexMap = buildHeaderIndex(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;

                List<String> cols = parseCsvLine(line);
                try {
                    tx.execute(status -> {
                        importSingleRow(indexMap, cols);
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

        String description = descriptionRaw == null ? null : descriptionRaw.trim();
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }

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
        headerLine = headerLine.replace("\uFEFF", "");
        List<String> headers = parseCsvLine(headerLine);
        Map<String, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = headers.get(i) == null ? "" : headers.get(i).trim();
            indexMap.put(key, i);
        }
        return indexMap;
    }

    private String getString(List<String> cols, Map<String, Integer> idx, String colName) {
        Integer i = idx.get(colName);
        if (i == null || i < 0 || i >= cols.size()) return null;
        String value = cols.get(i);
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
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

    private static String normalizeOptional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String rootMessage(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }

    private void safeClear() {
        try {
            if (em != null) em.clear();
        } catch (Exception ignored) {
        }
    }
}
