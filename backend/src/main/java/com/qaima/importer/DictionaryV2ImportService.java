package com.qaima.importer;

import com.qaima.common.DictionaryTermNormalizer;
import com.qaima.domain.DictionaryAlias;
import com.qaima.domain.DictionaryTerm;
import com.qaima.repository.DictionaryAliasRepository;
import com.qaima.repository.DictionaryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryV2ImportService {

    private static final List<String> EXPECTED_HEADERS = List.of(
            "entry_type",
            "display_term",
            "canonical_term",
            "description",
            "source",
            "source_org",
            "source_url",
            "source_type",
            "status",
            "reviewed_at",
            "tag",
            "alias_source_type",
            "alias_notes"
    );

    private static final Set<String> VALID_ENTRY_TYPES = Set.of("TERM", "ALIAS");
    private static final Set<String> VALID_STATUSES = Set.of("PUBLISHED", "DRAFT", "REVIEW_REQUIRED", "ARCHIVED");

    private final DictionaryRepository dictionaryRepository;
    private final DictionaryAliasRepository dictionaryAliasRepository;
    private final PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager em;

    public ImportSummary importFromCsv(Path csvPath, boolean dryRun) throws IOException {
        FlatDictionaryFile flatFile = readFlatFile(csvPath);
        ValidationResult validation = validate(flatFile);
        if (validation.hasErrors()) {
            throw new IllegalArgumentException(validation.toErrorMessage());
        }

        logValidationWarnings(validation);
        if (dryRun) {
            return ImportSummary.dryRun(csvPath, flatFile.rows(), validation.warningCount());
        }

        MutationCounts counts = applyImport(flatFile);
        return ImportSummary.applied(csvPath, flatFile.rows(), validation.warningCount(), counts);
    }

    private MutationCounts applyImport(FlatDictionaryFile flatFile) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        return tx.execute(status -> {
            MutationCounts counts = new MutationCounts();
            Map<String, DictionaryTerm> canonicalByKey = new HashMap<>();

            for (FlatRow row : flatFile.termRows()) {
                String termKey = normalizeRequired(row.displayTerm(), "term is required");
                boolean exists = dictionaryRepository.existsById(termKey);

                DictionaryTerm entity = exists
                        ? dictionaryRepository.findById(termKey).orElseThrow()
                        : new DictionaryTerm(termKey);
                entity.setTerm(termKey);
                entity.setDescription(row.description());
                entity.setSource(row.source());
                entity.setSourceOrg(row.sourceOrg());
                entity.setSourceUrl(row.sourceUrl());
                entity.setSourceType(row.sourceType());
                entity.setStatus(normalizeStatus(row.status()));
                entity.setReviewedAt(parseReviewedAt(row.reviewedAt()));
                entity.setTag(row.tag());

                DictionaryTerm saved = dictionaryRepository.save(entity);
                canonicalByKey.put(termKey, saved);
                if (exists) {
                    counts.termUpdated++;
                } else {
                    counts.termInserted++;
                }
            }

            dictionaryRepository.flush();
            safeClear();

            for (FlatRow row : flatFile.aliasRows()) {
                String normalizedAlias = normalizeRequired(row.displayTerm(), "alias is required");
                String canonicalKey = normalizeRequired(row.canonicalTerm(), "canonical_term is required");

                if (dictionaryRepository.existsById(normalizedAlias)) {
                    throw new IllegalArgumentException("Alias duplicates an existing dictionary term: " + row.displayTerm());
                }

                DictionaryTerm canonicalTerm = canonicalByKey.get(canonicalKey);
                if (canonicalTerm == null) {
                    canonicalTerm = dictionaryRepository.findById(canonicalKey)
                            .orElseThrow(() -> new IllegalArgumentException("Unknown canonical term: " + row.canonicalTerm()));
                    canonicalByKey.put(canonicalKey, canonicalTerm);
                }

                DictionaryAlias entity = dictionaryAliasRepository.findByNormalizedAliasTerm(normalizedAlias)
                        .orElseGet(DictionaryAlias::new);
                boolean exists = entity.getAliasId() != null;

                entity.setCanonicalTerm(canonicalTerm);
                entity.setAliasTerm(row.displayTerm());
                entity.setSourceType(normalizeOptional(row.aliasSourceType()));
                entity.setNotes(normalizeOptional(row.aliasNotes()));
                dictionaryAliasRepository.save(entity);

                if (exists) {
                    counts.aliasUpdated++;
                } else {
                    counts.aliasInserted++;
                }
            }

            dictionaryAliasRepository.flush();
            safeClear();
            verifyImportedDictionary();
            return counts;
        });
    }

    private FlatDictionaryFile readFlatFile(Path csvPath) throws IOException {
        if (csvPath == null || !Files.isRegularFile(csvPath)) {
            throw new IOException("CSV file not found: " + csvPath);
        }

        List<FlatRow> rows = new ArrayList<>();
        List<String> header;
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty CSV file: " + csvPath);
            }
            header = parseCsvLine(headerLine.replace("\uFEFF", ""));

            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                List<String> cols = parseCsvLine(line);
                rows.add(FlatRow.from(lineNo, header, cols));
            }
        }

        return new FlatDictionaryFile(header, rows);
    }

    private ValidationResult validate(FlatDictionaryFile flatFile) {
        ValidationResult result = new ValidationResult();

        if (!EXPECTED_HEADERS.equals(flatFile.header())) {
            result.addError("CSV header mismatch. expected=" + EXPECTED_HEADERS + ", actual=" + flatFile.header());
        }

        Map<String, FlatRow> termByKey = new LinkedHashMap<>();
        Map<String, String> seenDisplayTerms = new LinkedHashMap<>();
        Set<String> seenAliasKeys = new HashSet<>();

        for (FlatRow row : flatFile.rows()) {
            if (row.columnCount() != flatFile.header().size()) {
                result.addError(row.linePrefix() + "column count mismatch: expected "
                        + flatFile.header().size() + ", actual " + row.columnCount());
            }

            if (!VALID_ENTRY_TYPES.contains(row.entryType())) {
                result.addError(row.linePrefix() + "invalid entry_type: " + row.entryType());
                continue;
            }

            validateRequired(row, result);
            validateReviewedAt(row, result);

            String displayKey = DictionaryTermNormalizer.normalizeTerm(row.displayTerm());
            String canonicalKey = DictionaryTermNormalizer.normalizeTerm(row.canonicalTerm());
            if (displayKey == null || canonicalKey == null) {
                continue;
            }

            String previousDisplay = seenDisplayTerms.putIfAbsent(displayKey, row.displayTerm());
            if (previousDisplay != null) {
                result.addError(row.linePrefix() + "duplicate normalized display_term: " + row.displayTerm());
            }

            if ("TERM".equals(row.entryType())) {
                if (!displayKey.equals(canonicalKey)) {
                    result.addError(row.linePrefix() + "TERM canonical_term must equal display_term: " + row.displayTerm());
                }
                if (normalizeOptional(row.aliasSourceType()) != null || normalizeOptional(row.aliasNotes()) != null) {
                    result.addError(row.linePrefix() + "TERM row must not contain alias metadata: " + row.displayTerm());
                }
                if (termByKey.putIfAbsent(displayKey, row) != null) {
                    result.addError(row.linePrefix() + "duplicate TERM: " + row.displayTerm());
                }
            } else {
                if (displayKey.equals(canonicalKey)) {
                    result.addError(row.linePrefix() + "ALIAS must differ from canonical_term: " + row.displayTerm());
                }
                if (!seenAliasKeys.add(displayKey)) {
                    result.addError(row.linePrefix() + "duplicate ALIAS: " + row.displayTerm());
                }
            }

            if (normalizeOptional(row.sourceUrl()) == null) {
                result.blankSourceUrl++;
                if (!"internal_definition".equals(row.sourceType())) {
                    result.nonInternalBlankSourceUrl++;
                }
                if ("market_reference".equals(row.sourceType())) {
                    result.marketReferenceBlankSourceUrl++;
                }
            }
        }

        for (FlatRow row : flatFile.aliasRows()) {
            String displayKey = DictionaryTermNormalizer.normalizeTerm(row.displayTerm());
            String canonicalKey = DictionaryTermNormalizer.normalizeTerm(row.canonicalTerm());
            if (displayKey == null || canonicalKey == null) {
                continue;
            }

            if (termByKey.containsKey(displayKey)) {
                result.addError(row.linePrefix() + "ALIAS duplicates a canonical TERM: " + row.displayTerm());
            }

            FlatRow canonical = termByKey.get(canonicalKey);
            if (canonical == null) {
                result.addError(row.linePrefix() + "missing canonical TERM: " + row.canonicalTerm());
                continue;
            }

            validateAliasCopiesCanonicalFields(row, canonical, result);
        }

        return result;
    }

    private void validateRequired(FlatRow row, ValidationResult result) {
        require(row, row.displayTerm(), "display_term", result);
        require(row, row.canonicalTerm(), "canonical_term", result);
        require(row, row.description(), "description", result);
        require(row, row.source(), "source", result);
        require(row, row.sourceOrg(), "source_org", result);
        require(row, row.sourceType(), "source_type", result);
        require(row, row.status(), "status", result);
        require(row, row.reviewedAt(), "reviewed_at", result);
        require(row, row.tag(), "tag", result);

        if (!VALID_STATUSES.contains(row.status())) {
            result.addError(row.linePrefix() + "invalid status: " + row.status());
        }

        if ("ALIAS".equals(row.entryType())) {
            require(row, row.aliasSourceType(), "alias_source_type", result);
        }
    }

    private void validateReviewedAt(FlatRow row, ValidationResult result) {
        if (normalizeOptional(row.reviewedAt()) == null) {
            return;
        }
        try {
            parseReviewedAt(row.reviewedAt());
        } catch (IllegalArgumentException e) {
            result.addError(row.linePrefix() + e.getMessage());
        }
    }

    private void validateAliasCopiesCanonicalFields(FlatRow row, FlatRow canonical, ValidationResult result) {
        compareAliasField(row, canonical, "description", row.description(), canonical.description(), result);
        compareAliasField(row, canonical, "source", row.source(), canonical.source(), result);
        compareAliasField(row, canonical, "source_org", row.sourceOrg(), canonical.sourceOrg(), result);
        compareAliasField(row, canonical, "source_url", row.sourceUrl(), canonical.sourceUrl(), result);
        compareAliasField(row, canonical, "source_type", row.sourceType(), canonical.sourceType(), result);
        compareAliasField(row, canonical, "status", row.status(), canonical.status(), result);
        compareAliasField(row, canonical, "reviewed_at", row.reviewedAt(), canonical.reviewedAt(), result);
        compareAliasField(row, canonical, "tag", row.tag(), canonical.tag(), result);
    }

    private void logValidationWarnings(ValidationResult validation) {
        if (validation.blankSourceUrl > 0) {
            log.info(
                    "dictionary v2 source_url blanks: total={}, nonInternal={}, marketReference={}",
                    validation.blankSourceUrl,
                    validation.nonInternalBlankSourceUrl,
                    validation.marketReferenceBlankSourceUrl
            );
        }
    }

    private void verifyImportedDictionary() {
        verifyTerm("PER");
        verifyTerm("PBR");
        verifyTerm("PSR");
        verifyTerm("LCR");
        verifyTerm("NSFR");
        verifyTerm("TFP");
        verifyTerm("공헌이익");
        verifyAlias("P/E", "PER");
        verifyAlias("Price/Book", "PBR");
        verifyAlias("Price/Sales", "PSR");
        verifyAlias("한계이익", "공헌이익");
        verifyAlias("유동성커버리지비율", "LCR");
        verifyMissingAlias("총요소생산성약어");
    }

    private void verifyTerm(String rawTerm) {
        String term = normalizeRequired(rawTerm, "term is required");
        if (!dictionaryRepository.existsById(term)) {
            throw new IllegalStateException("Post-import verification failed. Missing term: " + rawTerm);
        }
    }

    private void verifyAlias(String rawAlias, String rawCanonicalTerm) {
        String alias = normalizeRequired(rawAlias, "alias is required");
        String canonicalTerm = normalizeRequired(rawCanonicalTerm, "canonical_term is required");
        DictionaryAlias entity = dictionaryAliasRepository.findByNormalizedAliasTerm(alias)
                .orElseThrow(() -> new IllegalStateException("Post-import verification failed. Missing alias: " + rawAlias));
        String actualCanonical = entity.getCanonicalTerm() == null ? null : entity.getCanonicalTerm().getTerm();
        if (!canonicalTerm.equals(actualCanonical)) {
            throw new IllegalStateException("Post-import verification failed. Alias " + rawAlias
                    + " expected canonical " + rawCanonicalTerm + " but was " + actualCanonical);
        }
    }

    private void verifyMissingAlias(String rawAlias) {
        String alias = normalizeRequired(rawAlias, "alias is required");
        if (dictionaryAliasRepository.existsByNormalizedAliasTerm(alias)) {
            throw new IllegalStateException("Post-import verification failed. Unexpected alias exists: " + rawAlias);
        }
    }

    private static void require(FlatRow row, String value, String fieldName, ValidationResult result) {
        if (normalizeOptional(value) == null) {
            result.addError(row.linePrefix() + fieldName + " is required");
        }
    }

    private static void compareAliasField(
            FlatRow row,
            FlatRow canonical,
            String field,
            String aliasValue,
            String canonicalValue,
            ValidationResult result
    ) {
        if (!normalizeComparable(aliasValue).equals(normalizeComparable(canonicalValue))) {
            result.addError(row.linePrefix() + "ALIAS " + field + " must match canonical row: "
                    + row.displayTerm() + " -> " + canonical.displayTerm());
        }
    }

    private static String normalizeRequired(String value, String message) {
        String normalized = DictionaryTermNormalizer.normalizeTerm(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "PUBLISHED" : normalized.toUpperCase();
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String normalizeComparable(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "" : normalized;
    }

    private static Instant parseReviewedAt(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }

        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(normalized).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid reviewed_at: " + value);
        }
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

    private void safeClear() {
        try {
            if (em != null) {
                em.clear();
            }
        } catch (Exception ignored) {
        }
    }

    private record FlatDictionaryFile(List<String> header, List<FlatRow> rows) {

        private List<FlatRow> termRows() {
            return rows.stream().filter(row -> "TERM".equals(row.entryType())).toList();
        }

        private List<FlatRow> aliasRows() {
            return rows.stream().filter(row -> "ALIAS".equals(row.entryType())).toList();
        }
    }

    private record FlatRow(
            int lineNo,
            int columnCount,
            String entryType,
            String displayTerm,
            String canonicalTerm,
            String description,
            String source,
            String sourceOrg,
            String sourceUrl,
            String sourceType,
            String status,
            String reviewedAt,
            String tag,
            String aliasSourceType,
            String aliasNotes
    ) {

        private static FlatRow from(int lineNo, List<String> header, List<String> cols) {
            Map<String, Integer> idx = new HashMap<>();
            for (int i = 0; i < header.size(); i++) {
                idx.put(header.get(i), i);
            }

            return new FlatRow(
                    lineNo,
                    cols.size(),
                    get(cols, idx, "entry_type").toUpperCase(),
                    get(cols, idx, "display_term"),
                    get(cols, idx, "canonical_term"),
                    get(cols, idx, "description"),
                    get(cols, idx, "source"),
                    get(cols, idx, "source_org"),
                    get(cols, idx, "source_url"),
                    get(cols, idx, "source_type"),
                    get(cols, idx, "status").toUpperCase(),
                    get(cols, idx, "reviewed_at"),
                    get(cols, idx, "tag"),
                    get(cols, idx, "alias_source_type"),
                    get(cols, idx, "alias_notes")
            );
        }

        private static String get(List<String> cols, Map<String, Integer> idx, String colName) {
            Integer index = idx.get(colName);
            if (index == null || index < 0 || index >= cols.size()) {
                return "";
            }
            String value = cols.get(index);
            return value == null ? "" : value.trim();
        }

        private String linePrefix() {
            return "line " + lineNo + ": ";
        }
    }

    private static final class ValidationResult {

        private final List<String> errors = new ArrayList<>();
        private int blankSourceUrl;
        private int nonInternalBlankSourceUrl;
        private int marketReferenceBlankSourceUrl;

        private void addError(String error) {
            errors.add(error);
        }

        private boolean hasErrors() {
            return !errors.isEmpty();
        }

        private int warningCount() {
            int warnings = 0;
            if (blankSourceUrl > 0) warnings++;
            if (nonInternalBlankSourceUrl > 0) warnings++;
            if (marketReferenceBlankSourceUrl > 0) warnings++;
            return warnings;
        }

        private String toErrorMessage() {
            return "dictionary_v2 validation failed: " + errors.size() + " error(s). First errors: "
                    + String.join(" | ", errors.stream().limit(20).toList());
        }
    }

    private static final class MutationCounts {

        private int termInserted;
        private int termUpdated;
        private int aliasInserted;
        private int aliasUpdated;
    }

    public record ImportSummary(
            Path csvPath,
            boolean dryRun,
            int totalRows,
            int termRows,
            int aliasRows,
            int warningCount,
            int termInserted,
            int termUpdated,
            int aliasInserted,
            int aliasUpdated
    ) {

        private static ImportSummary dryRun(Path csvPath, List<FlatRow> rows, int warningCount) {
            int termRows = (int) rows.stream().filter(row -> "TERM".equals(row.entryType())).count();
            int aliasRows = (int) rows.stream().filter(row -> "ALIAS".equals(row.entryType())).count();
            return new ImportSummary(csvPath, true, rows.size(), termRows, aliasRows, warningCount, 0, 0, 0, 0);
        }

        private static ImportSummary applied(Path csvPath, List<FlatRow> rows, int warningCount, MutationCounts counts) {
            int termRows = (int) rows.stream().filter(row -> "TERM".equals(row.entryType())).count();
            int aliasRows = (int) rows.stream().filter(row -> "ALIAS".equals(row.entryType())).count();
            return new ImportSummary(
                    csvPath,
                    false,
                    rows.size(),
                    termRows,
                    aliasRows,
                    warningCount,
                    counts.termInserted,
                    counts.termUpdated,
                    counts.aliasInserted,
                    counts.aliasUpdated
            );
        }

        public String toLogString() {
            return "dryRun=" + dryRun
                    + ", totalRows=" + totalRows
                    + ", termRows=" + termRows
                    + ", aliasRows=" + aliasRows
                    + ", warningCount=" + warningCount
                    + ", termInserted=" + termInserted
                    + ", termUpdated=" + termUpdated
                    + ", aliasInserted=" + aliasInserted
                    + ", aliasUpdated=" + aliasUpdated
                    + ", csvPath=" + csvPath;
        }
    }
}
