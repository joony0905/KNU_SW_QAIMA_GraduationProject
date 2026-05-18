package com.qaima.external;

import com.qaima.dto.sec.Sec13fDataSetParseResult;
import com.qaima.dto.sec.Sec13fDataSetParseResult.CoverPageRow;
import com.qaima.dto.sec.Sec13fDataSetParseResult.HoldingRow;
import com.qaima.dto.sec.Sec13fDataSetParseResult.Stats;
import com.qaima.dto.sec.Sec13fDataSetParseResult.SubmissionRow;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.springframework.stereotype.Component;

@Component
public class Sec13fDataSetParser {

    private static final DateTimeFormatter SEC_DATE_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd-MMM-yyyy")
            .toFormatter(Locale.ENGLISH);

    public Sec13fDataSetParseResult parse(Path zipPath, Set<String> mappedCusips) throws IOException {
        if (zipPath == null) {
            throw new IllegalArgumentException("zipPath is required");
        }
        Set<String> safeMappedCusips = mappedCusips == null ? Set.of() : mappedCusips;

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ParseCounters counters = new ParseCounters();
            Map<String, SubmissionRow> submissions = readSubmissions(zipFile, counters);
            Map<String, CoverPageRow> coverPages = readCoverPages(zipFile, counters);
            List<HoldingRow> holdings = readHoldings(zipFile, safeMappedCusips, counters);

            return new Sec13fDataSetParseResult(
                    zipPath,
                    zipPath.getFileName().toString(),
                    submissions,
                    coverPages,
                    holdings,
                    counters.toStats(holdings.size())
            );
        }
    }

    private Map<String, SubmissionRow> readSubmissions(
            ZipFile zipFile,
            ParseCounters counters
    ) throws IOException {
        Map<String, SubmissionRow> rows = new HashMap<>();
        try (BufferedReader reader = reader(zipFile, "SUBMISSION.tsv")) {
            TsvHeader header = TsvHeader.read(reader);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                counters.submissionRows++;
                String[] columns = split(line);
                String accessionNumber = value(columns, header, "ACCESSION_NUMBER");
                if (accessionNumber.isBlank()) {
                    continue;
                }
                rows.put(accessionNumber, new SubmissionRow(
                        accessionNumber,
                        parseSecDate(value(columns, header, "FILING_DATE")),
                        value(columns, header, "SUBMISSIONTYPE"),
                        value(columns, header, "CIK"),
                        parseSecDate(value(columns, header, "PERIODOFREPORT"))
                ));
            }
        }
        return rows;
    }

    private Map<String, CoverPageRow> readCoverPages(
            ZipFile zipFile,
            ParseCounters counters
    ) throws IOException {
        Map<String, CoverPageRow> rows = new HashMap<>();
        try (BufferedReader reader = reader(zipFile, "COVERPAGE.tsv")) {
            TsvHeader header = TsvHeader.read(reader);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                counters.coverPageRows++;
                String[] columns = split(line);
                String accessionNumber = value(columns, header, "ACCESSION_NUMBER");
                if (accessionNumber.isBlank()) {
                    continue;
                }
                rows.put(accessionNumber, new CoverPageRow(
                        accessionNumber,
                        parseSecDate(value(columns, header, "REPORTCALENDARORQUARTER")),
                        "Y".equalsIgnoreCase(value(columns, header, "ISAMENDMENT")),
                        value(columns, header, "AMENDMENTNO"),
                        value(columns, header, "AMENDMENTTYPE"),
                        value(columns, header, "FILINGMANAGER_NAME")
                ));
            }
        }
        return rows;
    }

    private List<HoldingRow> readHoldings(
            ZipFile zipFile,
            Set<String> mappedCusips,
            ParseCounters counters
    ) throws IOException {
        Map<String, MutableHolding> holdings = new HashMap<>();
        try (BufferedReader reader = reader(zipFile, "INFOTABLE.tsv")) {
            TsvHeader header = TsvHeader.read(reader);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                counters.infoTableRows++;
                String[] columns = split(line);
                String cusip = normalize(value(columns, header, "CUSIP"));
                if (!mappedCusips.contains(cusip)) {
                    counters.skippedUnmappedRows++;
                    continue;
                }
                if (!value(columns, header, "PUTCALL").isBlank()) {
                    counters.skippedDerivativeRows++;
                    continue;
                }
                if (!"SH".equalsIgnoreCase(value(columns, header, "SSHPRNAMTTYPE"))) {
                    counters.skippedNonShareRows++;
                    continue;
                }

                counters.matchedInfoTableRows++;
                String accessionNumber = value(columns, header, "ACCESSION_NUMBER");
                String key = accessionNumber + "|" + cusip;
                MutableHolding holding = holdings.computeIfAbsent(key, ignored -> new MutableHolding(
                        accessionNumber,
                        cusip,
                        value(columns, header, "NAMEOFISSUER"),
                        value(columns, header, "TITLEOFCLASS")
                ));
                holding.add(
                        parseDecimal(value(columns, header, "SSHPRNAMT")),
                        parseDecimal(value(columns, header, "VALUE"))
                );
            }
        }
        return holdings.values().stream()
                .map(MutableHolding::toRow)
                .toList();
    }

    private BufferedReader reader(ZipFile zipFile, String baseName) throws IOException {
        ZipEntry entry = zipFile.stream()
                .filter(candidate -> !candidate.isDirectory())
                .filter(candidate -> candidate.getName().equalsIgnoreCase(baseName)
                        || candidate.getName().toUpperCase(Locale.ROOT).endsWith("/" + baseName.toUpperCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(baseName + " not found in " + zipFile.getName()));
        return new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8));
    }

    private static String[] split(String line) {
        return line.split("\t", -1);
    }

    private static String value(String[] columns, TsvHeader header, String name) {
        Integer index = header.indexByName().get(name);
        if (index == null || index < 0 || index >= columns.length) {
            return "";
        }
        return columns[index].trim();
    }

    private static LocalDate parseSecDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value.trim(), SEC_DATE_FORMAT);
    }

    private static BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim().replace(",", ""));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private record TsvHeader(Map<String, Integer> indexByName) {
        static TsvHeader read(BufferedReader reader) throws IOException {
            String line = reader.readLine();
            if (line == null) {
                throw new IllegalArgumentException("TSV header is missing");
            }
            String[] headers = split(line.replace("\uFEFF", ""));
            Map<String, Integer> index = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                index.put(headers[i].trim(), i);
            }
            return new TsvHeader(index);
        }
    }

    private static class MutableHolding {
        private final String accessionNumber;
        private final String cusip;
        private final String nameOfIssuer;
        private final String titleOfClass;
        private int filingRowCount;
        private BigDecimal shares = BigDecimal.ZERO;
        private BigDecimal valueRaw = BigDecimal.ZERO;

        private MutableHolding(
                String accessionNumber,
                String cusip,
                String nameOfIssuer,
                String titleOfClass
        ) {
            this.accessionNumber = accessionNumber;
            this.cusip = cusip;
            this.nameOfIssuer = nameOfIssuer;
            this.titleOfClass = titleOfClass;
        }

        private void add(BigDecimal rowShares, BigDecimal rowValue) {
            filingRowCount++;
            shares = shares.add(rowShares);
            valueRaw = valueRaw.add(rowValue);
        }

        private HoldingRow toRow() {
            return new HoldingRow(
                    accessionNumber,
                    cusip,
                    nameOfIssuer,
                    titleOfClass,
                    filingRowCount,
                    shares,
                    valueRaw
            );
        }
    }

    private static class ParseCounters {
        private long submissionRows;
        private long coverPageRows;
        private long infoTableRows;
        private long matchedInfoTableRows;
        private long skippedUnmappedRows;
        private long skippedDerivativeRows;
        private long skippedNonShareRows;

        private Stats toStats(long parsedHoldingRows) {
            return new Stats(
                    submissionRows,
                    coverPageRows,
                    infoTableRows,
                    matchedInfoTableRows,
                    skippedUnmappedRows,
                    skippedDerivativeRows,
                    skippedNonShareRows,
                    parsedHoldingRows
            );
        }
    }
}
