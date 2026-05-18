package com.qaima.dto.sec;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record Sec13fDataSetParseResult(
        Path sourcePath,
        String sourceFile,
        Map<String, SubmissionRow> submissionsByAccession,
        Map<String, CoverPageRow> coverPagesByAccession,
        List<HoldingRow> holdings,
        Stats stats
) {

    public record SubmissionRow(
            String accessionNumber,
            LocalDate filingDate,
            String submissionType,
            String managerCik,
            LocalDate periodOfReport
    ) {
    }

    public record CoverPageRow(
            String accessionNumber,
            LocalDate reportCalendarOrQuarter,
            boolean amendment,
            String amendmentNo,
            String amendmentType,
            String managerName
    ) {
    }

    public record HoldingRow(
            String accessionNumber,
            String cusip,
            String nameOfIssuer,
            String titleOfClass,
            int filingRowCount,
            BigDecimal shares,
            BigDecimal valueRaw
    ) {
    }

    public record Stats(
            long submissionRows,
            long coverPageRows,
            long infoTableRows,
            long matchedInfoTableRows,
            long skippedUnmappedRows,
            long skippedDerivativeRows,
            long skippedNonShareRows,
            long parsedHoldingRows
    ) {
    }
}
