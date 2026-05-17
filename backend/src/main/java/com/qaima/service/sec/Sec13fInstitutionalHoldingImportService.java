package com.qaima.service.sec;

import com.qaima.common.Blocking;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.Sec13fFiling;
import com.qaima.domain.Sec13fHolding;
import com.qaima.domain.Sec13fImportFile;
import com.qaima.domain.Stock;
import com.qaima.domain.StockInstitutionalHoldingQuarterly;
import com.qaima.domain.StockSecurityIdentifier;
import com.qaima.dto.sec.Sec13fDataSetParseResult;
import com.qaima.dto.sec.Sec13fDataSetParseResult.CoverPageRow;
import com.qaima.dto.sec.Sec13fDataSetParseResult.HoldingRow;
import com.qaima.dto.sec.Sec13fDataSetParseResult.Stats;
import com.qaima.dto.sec.Sec13fDataSetParseResult.SubmissionRow;
import com.qaima.external.Sec13fDataSetParser;
import com.qaima.repository.IssuedSharesRepository;
import com.qaima.repository.Sec13fFilingRepository;
import com.qaima.repository.Sec13fHoldingRepository;
import com.qaima.repository.Sec13fImportFileRepository;
import com.qaima.repository.StockInstitutionalHoldingQuarterlyRepository;
import com.qaima.repository.StockRepository;
import com.qaima.repository.StockSecurityIdentifierRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class Sec13fInstitutionalHoldingImportService {

    public static final String SOURCE = StockInstitutionalHoldingQuarterly.SOURCE;
    private static final LocalDate VALUE_UNIT_CUTOFF_DATE = LocalDate.of(2023, 1, 3);
    private static final String VALUE_UNIT_USD = "USD";
    private static final String VALUE_UNIT_THOUSANDS_USD = "THOUSANDS_USD";
    private static final String SHARE_TYPE_SEC_COMMON = "COMMON";
    private static final String SHARE_TYPE_SEC_TOTAL = "TOTAL";
    private static final int DB_LOOKUP_BATCH_SIZE = 500;
    private static final int AGGREGATE_LOOKUP_BATCH_SIZE = 100;
    private static final int DB_SAVE_BATCH_SIZE = 1_000;
    private static final String CUSIP_ADMIN_UPSERT_SOURCE = "SEC_13F_ADMIN_UPSERT";

    private final Sec13fDataSetParser sec13fDataSetParser;
    private final StockSecurityIdentifierRepository stockSecurityIdentifierRepository;
    private final Sec13fImportFileRepository sec13fImportFileRepository;
    private final Sec13fFilingRepository sec13fFilingRepository;
    private final Sec13fHoldingRepository sec13fHoldingRepository;
    private final StockInstitutionalHoldingQuarterlyRepository quarterlyRepository;
    private final IssuedSharesRepository issuedSharesRepository;
    private final StockRepository stockRepository;

    @Value("${sec.form13f.source-dir:}")
    private String defaultSourceDir;

    public Mono<ImportFileResult> importFile(String filePath, boolean aggregate) {
        return Blocking.call(() -> importFileBlocking(resolveFilePath(filePath), aggregate));
    }

    public Mono<ImportDirectoryResult> importDirectory(String sourceDir, int limit, boolean aggregate) {
        return Blocking.call(() -> importDirectoryBlocking(resolveSourceDir(sourceDir), limit, aggregate));
    }

    public Mono<List<QuarterlyHoldingResult>> findQuarterlyHoldings(String stockCode, int limit) {
        return Blocking.call(() -> {
            Stock stock = stockRepository.findByStockCodeWithExchange(stockCode)
                    .orElseThrow(() -> new IllegalArgumentException("stock not found: " + stockCode));
            int safeLimit = Math.max(1, Math.min(limit, 120));
            return quarterlyRepository
                    .findByStockAndSourceOrderByReportPeriodDesc(stock, SOURCE, PageRequest.of(0, safeLimit))
                    .stream()
                    .map(QuarterlyHoldingResult::from)
                    .toList();
        });
    }

    public Mono<AggregateRebuildResult> rebuildAggregates(Integer stockLimit) {
        return Blocking.call(() -> rebuildAggregatesBlocking(stockLimit));
    }

    public Mono<CusipMappingResult> upsertCusipMapping(
            String stockCode,
            String cusip,
            String issuerName,
            Integer confidence
    ) {
        return Blocking.call(() -> upsertCusipMappingBlocking(stockCode, cusip, issuerName, confidence));
    }

    private CusipMappingResult upsertCusipMappingBlocking(
            String stockCode,
            String cusip,
            String issuerName,
            Integer confidence
    ) {
        if (stockCode == null || stockCode.isBlank()) {
            throw new IllegalArgumentException("stockCode is required");
        }
        String normalizedCusip = normalize(cusip);
        if (normalizedCusip.isBlank()) {
            throw new IllegalArgumentException("cusip is required");
        }
        Stock stock = stockRepository.findByStockCodeWithExchange(stockCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("stock not found: " + stockCode));
        rejectActiveCusipConflict(stock, normalizedCusip);

        StockSecurityIdentifier identifier = stockSecurityIdentifierRepository
                .findByStockAndIdentifierTypeAndIdentifierValue(
                        stock,
                        StockSecurityIdentifier.TYPE_CUSIP,
                        normalizedCusip
                )
                .orElseGet(StockSecurityIdentifier::new);
        boolean isNew = identifier.getStockSecurityIdentifierId() == null;

        identifier.setStock(stock);
        identifier.setIdentifierType(StockSecurityIdentifier.TYPE_CUSIP);
        identifier.setIdentifierValue(normalizedCusip);
        identifier.setIssuerName(issuerName);
        identifier.setSource(CUSIP_ADMIN_UPSERT_SOURCE);
        identifier.setConfidence(confidence == null ? 100 : Math.max(0, Math.min(confidence, 100)));
        identifier.setActive(true);
        StockSecurityIdentifier saved = stockSecurityIdentifierRepository.save(identifier);

        return new CusipMappingResult(
                saved.getStockSecurityIdentifierId(),
                stock.getStockCode(),
                saved.getIdentifierValue(),
                saved.getIssuerName(),
                saved.getSource(),
                saved.getConfidence(),
                saved.getActive(),
                isNew
        );
    }

    private AggregateRebuildResult rebuildAggregatesBlocking(Integer stockLimit) {
        int safeLimit = stockLimit == null ? 0 : Math.max(0, stockLimit);
        Map<Long, List<LocalDate>> periodsByStockId = new LinkedHashMap<>();
        for (Sec13fHoldingRepository.StockPeriodProjection projection :
                sec13fHoldingRepository.findDistinctStockPeriods()) {
            Long stockId = projection.getStockId();
            LocalDate reportPeriod = projection.getReportPeriod();
            if (stockId == null || reportPeriod == null) {
                continue;
            }
            if (safeLimit > 0 && !periodsByStockId.containsKey(stockId) && periodsByStockId.size() >= safeLimit) {
                continue;
            }
            addPeriod(periodsByStockId, stockId, reportPeriod);
        }
        if (periodsByStockId.isEmpty()) {
            return new AggregateRebuildResult(0, 0, 0);
        }

        List<Long> stockIds = new ArrayList<>(periodsByStockId.keySet());
        Map<Long, Stock> stockById = new LinkedHashMap<>();
        for (Stock stock : stockRepository.findAllById(stockIds)) {
            stockById.put(stock.getStockId(), stock);
        }

        int stockPeriodCount = periodsByStockId.values().stream()
                .mapToInt(List::size)
                .sum();
        int changedRows = recalculateAggregates(stockById, periodsByStockId);
        return new AggregateRebuildResult(stockById.size(), stockPeriodCount, changedRows);
    }

    private void rejectActiveCusipConflict(Stock targetStock, String normalizedCusip) {
        for (StockSecurityIdentifier activeIdentifier :
                stockSecurityIdentifierRepository.findByIdentifierTypeAndIdentifierValueAndActiveTrue(
                        StockSecurityIdentifier.TYPE_CUSIP,
                        normalizedCusip
                )) {
            Stock mappedStock = activeIdentifier.getStock();
            if (mappedStock != null && !Objects.equals(mappedStock.getStockId(), targetStock.getStockId())) {
                throw new IllegalArgumentException(
                        "CUSIP already mapped to active stock: cusip=" + normalizedCusip
                                + ", stockCode=" + mappedStock.getStockCode()
                );
            }
        }
    }

    private ImportDirectoryResult importDirectoryBlocking(Path sourceDir, int limit, boolean aggregate) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            throw new IllegalArgumentException("SEC 13F sourceDir is not a directory: " + sourceDir);
        }
        int safeLimit = limit > 0 ? limit : Integer.MAX_VALUE;
        List<Path> zipFiles;
        try (var stream = Files.list(sourceDir)) {
            zipFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("_form13f.zip"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .limit(safeLimit)
                    .toList();
        }

        List<ImportFileResult> results = new ArrayList<>();
        for (Path zipFile : zipFiles) {
            results.add(importFileBlocking(zipFile, aggregate));
        }

        return new ImportDirectoryResult(
                sourceDir.toString(),
                zipFiles.size(),
                results.stream().filter(result -> "SUCCESS".equals(result.status())).count(),
                results.stream().filter(result -> "FAILED".equals(result.status())).count(),
                results
        );
    }

    private ImportFileResult importFileBlocking(Path zipPath, boolean aggregate) {
        if (!Files.isRegularFile(zipPath)) {
            throw new IllegalArgumentException("SEC 13F file not found: " + zipPath);
        }

        ImportFileStart importFileStart = startImportFile(zipPath);
        Sec13fImportFile importFile = importFileStart.importFile();
        try {
            Map<String, StockSecurityIdentifier> identifierByCusip = loadCusipMappings();
            if (identifierByCusip.isEmpty()) {
                throw new IllegalStateException("CUSIP mappings are missing");
            }

            Sec13fDataSetParseResult parsed = sec13fDataSetParser.parse(zipPath, identifierByCusip.keySet());
            UpsertCounters counters = upsertParsedData(
                    parsed,
                    identifierByCusip,
                    aggregate,
                    importFileStart.forceAggregate()
            );
            finishImportFile(importFile, parsed.stats(), counters, null);

            ImportFileResult result = toImportFileResult(importFile);
            log.info("[SEC13F] import complete. sourceFile={}, matchedRows={}, holdings={}, aggregatedRows={}",
                    result.sourceFile(), result.matchedInfoTableRows(), result.parsedHoldingRows(), result.aggregatedRows());
            return result;
        } catch (Exception e) {
            finishImportFile(importFile, null, null, e);
            log.warn("[SEC13F] import failed. sourceFile={}, cause={}", zipPath.getFileName(), e.getMessage(), e);
            return toImportFileResult(importFile);
        }
    }

    private ImportFileStart startImportFile(Path zipPath) {
        String sourceFile = zipPath.getFileName().toString();
        Sec13fImportFile entity = sec13fImportFileRepository.findBySourceFile(sourceFile)
                .orElseGet(Sec13fImportFile::new);
        boolean forceAggregate = entity.getSec13fImportFileId() == null || !"SUCCESS".equals(entity.getStatus());
        entity.setSourceFile(sourceFile);
        entity.setSourcePath(zipPath.toAbsolutePath().toString());
        entity.setStatus("STARTED");
        entity.setStartedAt(Instant.now());
        entity.setFinishedAt(null);
        entity.setErrorMessage(null);
        resetImportStats(entity);
        return new ImportFileStart(sec13fImportFileRepository.save(entity), forceAggregate);
    }

    private void finishImportFile(
            Sec13fImportFile importFile,
            Stats stats,
            UpsertCounters counters,
            Exception error
    ) {
        if (stats != null) {
            importFile.setSubmissionRows(stats.submissionRows());
            importFile.setCoverPageRows(stats.coverPageRows());
            importFile.setInfoTableRows(stats.infoTableRows());
            importFile.setMatchedInfoTableRows(stats.matchedInfoTableRows());
            importFile.setSkippedUnmappedRows(stats.skippedUnmappedRows());
            importFile.setSkippedDerivativeRows(stats.skippedDerivativeRows());
            importFile.setSkippedNonShareRows(stats.skippedNonShareRows());
            importFile.setParsedHoldingRows(stats.parsedHoldingRows());
        }
        if (counters != null) {
            importFile.setCreatedFilings(counters.createdFilings);
            importFile.setUpdatedFilings(counters.updatedFilings);
            importFile.setCreatedHoldings(counters.createdHoldings);
            importFile.setUpdatedHoldings(counters.updatedHoldings);
            importFile.setUnchangedHoldings(counters.unchangedHoldings);
            importFile.setAggregatedRows(counters.aggregatedRows);
        }
        importFile.setStatus(error == null ? "SUCCESS" : "FAILED");
        importFile.setErrorMessage(error == null ? null : truncate(error.getMessage(), 1000));
        importFile.setFinishedAt(Instant.now());
        sec13fImportFileRepository.save(importFile);
    }

    private void resetImportStats(Sec13fImportFile entity) {
        entity.setSubmissionRows(0L);
        entity.setCoverPageRows(0L);
        entity.setInfoTableRows(0L);
        entity.setMatchedInfoTableRows(0L);
        entity.setSkippedUnmappedRows(0L);
        entity.setSkippedDerivativeRows(0L);
        entity.setSkippedNonShareRows(0L);
        entity.setParsedHoldingRows(0L);
        entity.setCreatedFilings(0);
        entity.setUpdatedFilings(0);
        entity.setCreatedHoldings(0);
        entity.setUpdatedHoldings(0);
        entity.setUnchangedHoldings(0);
        entity.setAggregatedRows(0);
    }

    private Map<String, StockSecurityIdentifier> loadCusipMappings() {
        Map<String, StockSecurityIdentifier> result = new HashMap<>();
        for (StockSecurityIdentifier identifier :
                stockSecurityIdentifierRepository.findByIdentifierTypeAndActiveTrue(StockSecurityIdentifier.TYPE_CUSIP)) {
            String cusip = normalize(identifier.getIdentifierValue());
            StockSecurityIdentifier existing = result.get(cusip);
            if (existing == null || confidence(identifier) > confidence(existing)) {
                result.put(cusip, identifier);
            }
        }
        return result;
    }

    private UpsertCounters upsertParsedData(
            Sec13fDataSetParseResult parsed,
            Map<String, StockSecurityIdentifier> identifierByCusip,
            boolean aggregate,
            boolean forceAggregate
    ) {
        UpsertCounters counters = new UpsertCounters();
        Map<Long, Stock> parsedStocks = new LinkedHashMap<>();
        Map<Long, Stock> affectedStocks = new LinkedHashMap<>();
        Map<Long, List<LocalDate>> parsedPeriodsByStock = new LinkedHashMap<>();
        Map<Long, List<LocalDate>> affectedPeriodsByStock = new LinkedHashMap<>();
        Map<String, FilingContext> filingContexts = new LinkedHashMap<>();
        List<PreparedHolding> preparedHoldings = new ArrayList<>(parsed.holdings().size());

        for (HoldingRow row : parsed.holdings()) {
            SubmissionRow submission = parsed.submissionsByAccession().get(row.accessionNumber());
            if (submission == null || submission.managerCik() == null || submission.managerCik().isBlank()) {
                continue;
            }
            CoverPageRow coverPage = parsed.coverPagesByAccession().get(row.accessionNumber());
            LocalDate reportPeriod = resolveReportPeriod(submission, coverPage);
            if (reportPeriod == null || submission.filingDate() == null) {
                continue;
            }

            StockSecurityIdentifier mapping = identifierByCusip.get(normalize(row.cusip()));
            if (mapping == null || mapping.getStock() == null) {
                continue;
            }

            Stock stock = mapping.getStock();
            parsedStocks.putIfAbsent(stock.getStockId(), stock);
            addPeriod(parsedPeriodsByStock, stock.getStockId(), reportPeriod);
            filingContexts.putIfAbsent(
                    row.accessionNumber(),
                    new FilingContext(parsed.sourceFile(), submission, coverPage, reportPeriod)
            );
            preparedHoldings.add(new PreparedHolding(row, submission, reportPeriod, stock));
        }

        Map<String, Sec13fFiling> filingByAccession = loadExistingFilings(filingContexts.keySet());
        upsertFilings(filingContexts.values(), filingByAccession, counters);

        Map<HoldingKey, Sec13fHolding> existingHoldings = loadExistingHoldings(filingContexts.keySet());
        List<Sec13fHolding> holdingsToSave = new ArrayList<>(DB_SAVE_BATCH_SIZE);
        for (PreparedHolding prepared : preparedHoldings) {
            Sec13fFiling filing = filingByAccession.get(prepared.row().accessionNumber());
            if (filing == null) {
                continue;
            }

            HoldingKey key = new HoldingKey(
                    prepared.row().accessionNumber(),
                    prepared.stock().getStockId(),
                    prepared.row().cusip()
            );
            Sec13fHolding changed = applyHolding(
                    filing,
                    prepared.stock(),
                    prepared.submission(),
                    prepared.row(),
                    prepared.reportPeriod(),
                    existingHoldings.get(key),
                    counters
            );
            if (changed != null) {
                holdingsToSave.add(changed);
                affectedStocks.put(prepared.stock().getStockId(), prepared.stock());
                addPeriod(affectedPeriodsByStock, prepared.stock().getStockId(), prepared.reportPeriod());
            }
            if (holdingsToSave.size() >= DB_SAVE_BATCH_SIZE) {
                flushHoldings(holdingsToSave);
            }
        }
        flushHoldings(holdingsToSave);

        if (aggregate) {
            counters.aggregatedRows += recalculateAggregates(
                    forceAggregate ? parsedStocks : affectedStocks,
                    forceAggregate ? parsedPeriodsByStock : affectedPeriodsByStock
            );
        }
        return counters;
    }

    private void addPeriod(Map<Long, List<LocalDate>> periodsByStockId, Long stockId, LocalDate period) {
        if (stockId == null || period == null) {
            return;
        }
        List<LocalDate> periods = periodsByStockId.computeIfAbsent(stockId, ignored -> new ArrayList<>());
        if (!periods.contains(period)) {
            periods.add(period);
        }
    }

    private Map<String, Sec13fFiling> loadExistingFilings(Collection<String> accessionNumbers) {
        Map<String, Sec13fFiling> result = new HashMap<>();
        List<String> accessions = new ArrayList<>(accessionNumbers);
        for (int start = 0; start < accessions.size(); start += DB_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + DB_LOOKUP_BATCH_SIZE, accessions.size());
            for (Sec13fFiling filing : sec13fFilingRepository.findByAccessionNumberIn(accessions.subList(start, end))) {
                result.put(filing.getAccessionNumber(), filing);
            }
        }
        return result;
    }

    private Map<HoldingKey, Sec13fHolding> loadExistingHoldings(Collection<String> accessionNumbers) {
        Map<HoldingKey, Sec13fHolding> result = new HashMap<>();
        List<String> accessions = new ArrayList<>(accessionNumbers);
        for (int start = 0; start < accessions.size(); start += DB_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + DB_LOOKUP_BATCH_SIZE, accessions.size());
            for (Sec13fHolding holding : sec13fHoldingRepository.findByAccessionNumberIn(accessions.subList(start, end))) {
                result.put(HoldingKey.from(holding), holding);
            }
        }
        return result;
    }

    private void upsertFilings(
            Collection<FilingContext> contexts,
            Map<String, Sec13fFiling> filingByAccession,
            UpsertCounters counters
    ) {
        List<Sec13fFiling> filingsToSave = new ArrayList<>(DB_SAVE_BATCH_SIZE);
        for (FilingContext context : contexts) {
            Sec13fFiling entity = filingByAccession.getOrDefault(
                    context.submission().accessionNumber(),
                    new Sec13fFiling()
            );
            boolean isNew = entity.getSec13fFilingId() == null;
            if (!applyFiling(entity, context)) {
                continue;
            }

            if (isNew) {
                counters.createdFilings++;
            } else {
                counters.updatedFilings++;
            }
            filingsToSave.add(entity);
            if (filingsToSave.size() >= DB_SAVE_BATCH_SIZE) {
                flushFilings(filingsToSave, filingByAccession);
            }
        }
        flushFilings(filingsToSave, filingByAccession);
    }

    private void flushFilings(
            List<Sec13fFiling> filingsToSave,
            Map<String, Sec13fFiling> filingByAccession
    ) {
        if (filingsToSave.isEmpty()) {
            return;
        }
        for (Sec13fFiling saved : sec13fFilingRepository.saveAll(filingsToSave)) {
            filingByAccession.put(saved.getAccessionNumber(), saved);
        }
        filingsToSave.clear();
    }

    private boolean applyFiling(Sec13fFiling entity, FilingContext context) {
        SubmissionRow submission = context.submission();
        CoverPageRow coverPage = context.coverPage();
        boolean isNew = entity.getSec13fFilingId() == null;
        boolean amendment = coverPage != null && coverPage.amendment();
        String managerName = coverPage == null ? null : coverPage.managerName();
        String amendmentNo = coverPage == null ? null : coverPage.amendmentNo();
        String amendmentType = coverPage == null ? null : coverPage.amendmentType();

        boolean changed = isNew
                || !Objects.equals(entity.getManagerCik(), submission.managerCik())
                || !Objects.equals(entity.getManagerName(), managerName)
                || !Objects.equals(entity.getFilingDate(), submission.filingDate())
                || !Objects.equals(entity.getReportPeriod(), context.reportPeriod())
                || !Objects.equals(entity.getSubmissionType(), submission.submissionType())
                || !Objects.equals(entity.getAmendment(), amendment)
                || !Objects.equals(entity.getAmendmentNo(), amendmentNo)
                || !Objects.equals(entity.getAmendmentType(), amendmentType)
                || !Objects.equals(entity.getSourceFile(), context.sourceFile());

        if (changed) {
            entity.setAccessionNumber(submission.accessionNumber());
            entity.setManagerCik(submission.managerCik());
            entity.setManagerName(managerName);
            entity.setFilingDate(submission.filingDate());
            entity.setReportPeriod(context.reportPeriod());
            entity.setSubmissionType(submission.submissionType());
            entity.setAmendment(amendment);
            entity.setAmendmentNo(amendmentNo);
            entity.setAmendmentType(amendmentType);
            entity.setSourceFile(context.sourceFile());
        }
        return changed;
    }

    private Sec13fHolding applyHolding(
            Sec13fFiling filing,
            Stock stock,
            SubmissionRow submission,
            HoldingRow row,
            LocalDate reportPeriod,
            Sec13fHolding existing,
            UpsertCounters counters
    ) {
        ValueNormalization value = normalizeMarketValue(row.valueRaw(), submission.filingDate());
        Sec13fHolding entity = existing == null ? new Sec13fHolding() : existing;
        boolean isNew = entity.getSec13fHoldingId() == null;

        boolean changed = isNew
                || !Objects.equals(filingId(entity.getFiling()), filingId(filing))
                || !Objects.equals(stockId(entity.getStock()), stockId(stock))
                || !Objects.equals(entity.getManagerCik(), submission.managerCik())
                || !Objects.equals(entity.getReportPeriod(), reportPeriod)
                || !Objects.equals(entity.getFilingDate(), submission.filingDate())
                || !Objects.equals(entity.getNameOfIssuer(), row.nameOfIssuer())
                || !Objects.equals(entity.getTitleOfClass(), row.titleOfClass())
                || !Objects.equals(entity.getFilingRowCount(), row.filingRowCount())
                || compare(entity.getShares(), row.shares()) != 0
                || compare(entity.getValueRaw(), row.valueRaw()) != 0
                || !Objects.equals(entity.getValueUnit(), value.unit())
                || compare(entity.getMarketValueUsd(), value.marketValueUsd()) != 0;

        if (!changed) {
            counters.unchangedHoldings++;
            return null;
        }

        entity.setFiling(filing);
        entity.setStock(stock);
        entity.setAccessionNumber(row.accessionNumber());
        entity.setManagerCik(submission.managerCik());
        entity.setReportPeriod(reportPeriod);
        entity.setFilingDate(submission.filingDate());
        entity.setCusip(row.cusip());
        entity.setNameOfIssuer(row.nameOfIssuer());
        entity.setTitleOfClass(row.titleOfClass());
        entity.setFilingRowCount(row.filingRowCount());
        entity.setShares(row.shares());
        entity.setValueRaw(row.valueRaw());
        entity.setValueUnit(value.unit());
        entity.setMarketValueUsd(value.marketValueUsd());
        entity.setSource(SOURCE);

        if (isNew) {
            counters.createdHoldings++;
        } else {
            counters.updatedHoldings++;
        }
        return entity;
    }

    private void flushHoldings(List<Sec13fHolding> holdingsToSave) {
        if (holdingsToSave.isEmpty()) {
            return;
        }
        sec13fHoldingRepository.saveAll(holdingsToSave);
        holdingsToSave.clear();
    }

    private int recalculateAggregates(
            Map<Long, Stock> stockById,
            Map<Long, List<LocalDate>> changedPeriodsByStockId
    ) {
        if (stockById == null || stockById.isEmpty() || changedPeriodsByStockId == null || changedPeriodsByStockId.isEmpty()) {
            return 0;
        }
        List<Long> stockIds = new ArrayList<>(stockById.keySet());
        Map<Long, Map<LocalDate, StockInstitutionalHoldingQuarterly>> existingAggregates =
                loadExistingAggregates(stockIds);
        Map<Long, List<IssuedShares>> issuedSharesByStockId = loadIssuedShares(stockIds);
        Map<Long, List<LocalDate>> targetPeriodsByStockId = resolveTargetPeriods(
                changedPeriodsByStockId,
                existingAggregates
        );
        Map<LocalDate, List<Long>> stockIdsByTargetPeriod = invertTargetPeriods(targetPeriodsByStockId);
        if (stockIdsByTargetPeriod.isEmpty()) {
            return 0;
        }

        int changedRows = 0;
        List<StockInstitutionalHoldingQuarterly> aggregatesToSave = new ArrayList<>(DB_SAVE_BATCH_SIZE);
        Map<AggregateKey, Sec13fHoldingRepository.AggregatePeriodProjection> aggregateByKey = new HashMap<>();
        for (Map.Entry<LocalDate, List<Long>> entry : stockIdsByTargetPeriod.entrySet()) {
            LocalDate targetPeriod = entry.getKey();
            List<Long> periodStockIds = entry.getValue();
            for (int start = 0; start < periodStockIds.size(); start += AGGREGATE_LOOKUP_BATCH_SIZE) {
                int end = Math.min(start + AGGREGATE_LOOKUP_BATCH_SIZE, periodStockIds.size());
                List<Long> stockIdChunk = periodStockIds.subList(start, end);
                for (Sec13fHoldingRepository.AggregatePeriodProjection aggregate :
                        sec13fHoldingRepository.aggregateLatestByStockIdsAndReportPeriods(stockIdChunk, List.of(targetPeriod))) {
                    aggregateByKey.put(new AggregateKey(aggregate.getStockId(), aggregate.getReportPeriod()), aggregate);
                }
            }
        }

        for (int start = 0; start < stockIds.size(); start += DB_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + DB_LOOKUP_BATCH_SIZE, stockIds.size());
            for (Long stockId : stockIds.subList(start, end)) {
                List<LocalDate> targetPeriods = targetPeriodsByStockId.getOrDefault(stockId, List.of());
                if (targetPeriods.isEmpty()) {
                    continue;
                }
                Stock stock = stockById.get(stockId);
                Map<LocalDate, StockInstitutionalHoldingQuarterly> existingByPeriod =
                        existingAggregates.computeIfAbsent(stockId, ignored -> new HashMap<>());
                Map<LocalDate, StockInstitutionalHoldingQuarterly> processedByPeriod = new HashMap<>();

                for (LocalDate targetPeriod : targetPeriods) {
                    Sec13fHoldingRepository.AggregatePeriodProjection aggregate =
                            aggregateByKey.get(new AggregateKey(stockId, targetPeriod));
                    if (stock == null || aggregate == null) {
                        continue;
                    }

                    BigDecimal sharesHeld = zeroIfNull(aggregate.getSharesHeld());
                    StockInstitutionalHoldingQuarterly previous = findPreviousAggregate(
                            existingByPeriod,
                            processedByPeriod,
                            targetPeriod
                    );
                    BigDecimal sharesChange = previous == null ? null : sharesHeld.subtract(previous.getSharesHeld());
                    BigDecimal sharesChangeRate = null;
                    if (previous != null && previous.getSharesHeld() != null && previous.getSharesHeld().compareTo(BigDecimal.ZERO) != 0) {
                        sharesChangeRate = sharesChange.divide(previous.getSharesHeld(), 8, RoundingMode.HALF_UP);
                    }
                    BigDecimal sharesOutstanding = resolveSharesOutstanding(
                            issuedSharesByStockId,
                            stockId,
                            targetPeriod
                    );
                    BigDecimal holdingRatio = null;
                    if (sharesOutstanding != null && sharesOutstanding.compareTo(BigDecimal.ZERO) != 0) {
                        holdingRatio = sharesHeld.divide(sharesOutstanding, 8, RoundingMode.HALF_UP);
                    }

                    StockInstitutionalHoldingQuarterly entity = existingByPeriod
                            .getOrDefault(targetPeriod, new StockInstitutionalHoldingQuarterly());
                    boolean isNew = entity.getStockInstitutionalHoldingQuarterlyId() == null;
                    boolean changed = isNew
                            || !Objects.equals(stockId(entity.getStock()), stock.getStockId())
                            || !Objects.equals(entity.getStockCode(), stock.getStockCode())
                            || !Objects.equals(entity.getReportPeriod(), targetPeriod)
                            || !Objects.equals(entity.getCusip(), aggregate.getCusip())
                            || !Objects.equals(entity.getInstitutionCount(), aggregate.getInstitutionCount())
                            || !Objects.equals(entity.getFilingRowCount(), aggregate.getFilingRowCount())
                            || compare(entity.getSharesHeld(), sharesHeld) != 0
                            || compare(entity.getSharesChange(), sharesChange) != 0
                            || compare(entity.getSharesChangeRate(), sharesChangeRate) != 0
                            || compare(entity.getMarketValueUsd(), zeroIfNull(aggregate.getMarketValueUsd())) != 0
                            || compare(entity.getSharesOutstanding(), sharesOutstanding) != 0
                            || compare(entity.getHoldingRatio(), holdingRatio) != 0;

                    if (changed) {
                        entity.setStock(stock);
                        entity.setStockCode(stock.getStockCode());
                        entity.setReportPeriod(targetPeriod);
                        entity.setCusip(aggregate.getCusip());
                        entity.setInstitutionCount(aggregate.getInstitutionCount() == null ? 0 : aggregate.getInstitutionCount());
                        entity.setFilingRowCount(aggregate.getFilingRowCount() == null ? 0 : aggregate.getFilingRowCount());
                        entity.setSharesHeld(sharesHeld);
                        entity.setSharesChange(sharesChange);
                        entity.setSharesChangeRate(sharesChangeRate);
                        entity.setMarketValueUsd(zeroIfNull(aggregate.getMarketValueUsd()));
                        entity.setSharesOutstanding(sharesOutstanding);
                        entity.setHoldingRatio(holdingRatio);
                        entity.setSource(SOURCE);
                        aggregatesToSave.add(entity);
                        changedRows++;
                        if (aggregatesToSave.size() >= DB_SAVE_BATCH_SIZE) {
                            flushAggregates(aggregatesToSave);
                        }
                    }
                    processedByPeriod.put(targetPeriod, entity);
                }
            }
        }
        flushAggregates(aggregatesToSave);
        return changedRows;
    }

    private Map<LocalDate, List<Long>> invertTargetPeriods(Map<Long, List<LocalDate>> targetPeriodsByStockId) {
        Map<LocalDate, List<Long>> result = new LinkedHashMap<>();
        for (Map.Entry<Long, List<LocalDate>> entry : targetPeriodsByStockId.entrySet()) {
            Long stockId = entry.getKey();
            if (stockId == null) {
                continue;
            }
            for (LocalDate targetPeriod : entry.getValue()) {
                if (targetPeriod == null) {
                    continue;
                }
                result.computeIfAbsent(targetPeriod, ignored -> new ArrayList<>()).add(stockId);
            }
        }
        return result;
    }

    private Map<Long, List<LocalDate>> resolveTargetPeriods(
            Map<Long, List<LocalDate>> changedPeriodsByStockId,
            Map<Long, Map<LocalDate, StockInstitutionalHoldingQuarterly>> existingAggregates
    ) {
        Map<Long, List<LocalDate>> result = new HashMap<>();
        for (Map.Entry<Long, List<LocalDate>> entry : changedPeriodsByStockId.entrySet()) {
            TreeSet<LocalDate> targetPeriods = new TreeSet<>();
            TreeSet<LocalDate> existingPeriods = new TreeSet<>(
                    existingAggregates.getOrDefault(entry.getKey(), Map.of()).keySet()
            );
            for (LocalDate changedPeriod : entry.getValue()) {
                if (changedPeriod == null) {
                    continue;
                }
                targetPeriods.add(changedPeriod);
                LocalDate nextPeriod = existingPeriods.higher(changedPeriod);
                if (nextPeriod != null) {
                    targetPeriods.add(nextPeriod);
                }
            }
            if (!targetPeriods.isEmpty()) {
                result.put(entry.getKey(), new ArrayList<>(targetPeriods));
            }
        }
        return result;
    }

    private StockInstitutionalHoldingQuarterly findPreviousAggregate(
            Map<LocalDate, StockInstitutionalHoldingQuarterly> existingByPeriod,
            Map<LocalDate, StockInstitutionalHoldingQuarterly> processedByPeriod,
            LocalDate targetPeriod
    ) {
        StockInstitutionalHoldingQuarterly previous = null;
        for (StockInstitutionalHoldingQuarterly candidate : existingByPeriod.values()) {
            previous = laterPrevious(previous, candidate, targetPeriod);
        }
        for (StockInstitutionalHoldingQuarterly candidate : processedByPeriod.values()) {
            previous = laterPrevious(previous, candidate, targetPeriod);
        }
        return previous;
    }

    private StockInstitutionalHoldingQuarterly laterPrevious(
            StockInstitutionalHoldingQuarterly current,
            StockInstitutionalHoldingQuarterly candidate,
            LocalDate targetPeriod
    ) {
        if (candidate == null || candidate.getReportPeriod() == null || !candidate.getReportPeriod().isBefore(targetPeriod)) {
            return current;
        }
        if (current == null || current.getReportPeriod().isBefore(candidate.getReportPeriod())) {
            return candidate;
        }
        return current;
    }

    private Map<Long, Map<LocalDate, StockInstitutionalHoldingQuarterly>> loadExistingAggregates(List<Long> stockIds) {
        Map<Long, Map<LocalDate, StockInstitutionalHoldingQuarterly>> result = new HashMap<>();
        for (int start = 0; start < stockIds.size(); start += DB_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + DB_LOOKUP_BATCH_SIZE, stockIds.size());
            for (StockInstitutionalHoldingQuarterly entity :
                    quarterlyRepository.findByStockIdsAndSource(stockIds.subList(start, end), SOURCE)) {
                result.computeIfAbsent(entity.getStock().getStockId(), ignored -> new HashMap<>())
                        .put(entity.getReportPeriod(), entity);
            }
        }
        return result;
    }

    private Map<Long, List<IssuedShares>> loadIssuedShares(List<Long> stockIds) {
        Map<Long, List<IssuedShares>> result = new HashMap<>();
        for (int start = 0; start < stockIds.size(); start += DB_LOOKUP_BATCH_SIZE) {
            int end = Math.min(start + DB_LOOKUP_BATCH_SIZE, stockIds.size());
            for (IssuedShares issuedShares :
                    issuedSharesRepository.findResolvedCandidatesByStockIds(stockIds.subList(start, end))) {
                result.computeIfAbsent(issuedShares.getStock().getStockId(), ignored -> new ArrayList<>())
                        .add(issuedShares);
            }
        }
        return result;
    }

    private void flushAggregates(List<StockInstitutionalHoldingQuarterly> aggregatesToSave) {
        if (aggregatesToSave.isEmpty()) {
            return;
        }
        quarterlyRepository.saveAll(aggregatesToSave);
        aggregatesToSave.clear();
    }

    private AggregatedPeriod aggregatePeriod(Stock stock, LocalDate period) {
        List<Sec13fHolding> holdings = sec13fHoldingRepository
                .findByStockAndReportPeriodOrderByManagerCikAscFilingDateDescAccessionNumberDesc(stock, period);
        if (holdings.isEmpty()) {
            return null;
        }

        Map<String, Sec13fHolding> latestByManager = new LinkedHashMap<>();
        for (Sec13fHolding holding : holdings) {
            latestByManager.putIfAbsent(holding.getManagerCik(), holding);
        }

        BigDecimal sharesHeld = BigDecimal.ZERO;
        BigDecimal marketValueUsd = BigDecimal.ZERO;
        int filingRowCount = 0;
        String cusip = null;
        for (Sec13fHolding holding : latestByManager.values()) {
            sharesHeld = sharesHeld.add(holding.getShares());
            marketValueUsd = marketValueUsd.add(holding.getMarketValueUsd());
            filingRowCount += holding.getFilingRowCount();
            if (cusip == null) {
                cusip = holding.getCusip();
            }
        }

        return new AggregatedPeriod(
                period,
                cusip,
                latestByManager.size(),
                filingRowCount,
                sharesHeld,
                marketValueUsd
        );
    }

    private BigDecimal resolveSharesOutstanding(Stock stock, LocalDate period) {
        IssuedShares issuedShares = issuedSharesRepository
                .findTopByStockAndShareTypeAndIssuedSharesTotalIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                        stock,
                        SHARE_TYPE_SEC_COMMON,
                        period
                )
                .orElseGet(() -> issuedSharesRepository
                        .findTopByStockAndShareTypeAndIssuedSharesTotalIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                                stock,
                                SHARE_TYPE_SEC_TOTAL,
                                period
                        )
                        .orElseGet(() -> issuedSharesRepository
                                .findTopByStockAndBaseDateLessThanEqualOrderByBaseDateDesc(stock, period)
                                .orElse(null)));
        if (issuedShares == null || issuedShares.getIssuedSharesTotal() == null) {
            return null;
        }
        return BigDecimal.valueOf(issuedShares.getIssuedSharesTotal());
    }

    private BigDecimal resolveSharesOutstanding(
            Map<Long, List<IssuedShares>> issuedSharesByStockId,
            Long stockId,
            LocalDate period
    ) {
        List<IssuedShares> candidates = issuedSharesByStockId.getOrDefault(stockId, List.of());
        Long resolved = resolveSharesOutstanding(candidates, SHARE_TYPE_SEC_COMMON, period);
        if (resolved == null) {
            resolved = resolveSharesOutstanding(candidates, SHARE_TYPE_SEC_TOTAL, period);
        }
        if (resolved == null) {
            resolved = resolveSharesOutstanding(candidates, null, period);
        }
        return resolved == null ? null : BigDecimal.valueOf(resolved);
    }

    private Long resolveSharesOutstanding(List<IssuedShares> candidates, String shareType, LocalDate period) {
        for (IssuedShares candidate : candidates) {
            if (candidate.getIssuedSharesTotal() == null || candidate.getBaseDate() == null) {
                continue;
            }
            if (shareType != null && !shareType.equals(candidate.getShareType())) {
                continue;
            }
            if (!candidate.getBaseDate().isAfter(period)) {
                return candidate.getIssuedSharesTotal();
            }
        }
        return null;
    }

    private LocalDate resolveReportPeriod(SubmissionRow submission, CoverPageRow coverPage) {
        if (submission.periodOfReport() != null) {
            return submission.periodOfReport();
        }
        return coverPage == null ? null : coverPage.reportCalendarOrQuarter();
    }

    private ValueNormalization normalizeMarketValue(BigDecimal valueRaw, LocalDate filingDate) {
        BigDecimal safeValue = valueRaw == null ? BigDecimal.ZERO : valueRaw;
        if (filingDate != null && filingDate.isBefore(VALUE_UNIT_CUTOFF_DATE)) {
            return new ValueNormalization(VALUE_UNIT_THOUSANDS_USD, safeValue.multiply(BigDecimal.valueOf(1000)));
        }
        return new ValueNormalization(VALUE_UNIT_USD, safeValue);
    }

    private Path resolveFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath is required");
        }
        return Path.of(filePath.trim()).toAbsolutePath().normalize();
    }

    private Path resolveSourceDir(String sourceDir) {
        String resolved = sourceDir == null || sourceDir.isBlank() ? defaultSourceDir : sourceDir;
        if (resolved == null || resolved.isBlank()) {
            throw new IllegalArgumentException("sourceDir is required");
        }
        return Path.of(resolved.trim()).toAbsolutePath().normalize();
    }

    private static int confidence(StockSecurityIdentifier identifier) {
        return identifier.getConfidence() == null ? 0 : identifier.getConfidence();
    }

    private static Long filingId(Sec13fFiling filing) {
        return filing == null ? null : filing.getSec13fFilingId();
    }

    private static Long stockId(Stock stock) {
        return stock == null ? null : stock.getStockId();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private static int compare(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private ImportFileResult toImportFileResult(Sec13fImportFile entity) {
        return new ImportFileResult(
                entity.getSourceFile(),
                entity.getSourcePath(),
                entity.getStatus(),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getSubmissionRows(),
                entity.getCoverPageRows(),
                entity.getInfoTableRows(),
                entity.getMatchedInfoTableRows(),
                entity.getSkippedUnmappedRows(),
                entity.getSkippedDerivativeRows(),
                entity.getSkippedNonShareRows(),
                entity.getParsedHoldingRows(),
                entity.getCreatedFilings(),
                entity.getUpdatedFilings(),
                entity.getCreatedHoldings(),
                entity.getUpdatedHoldings(),
                entity.getUnchangedHoldings(),
                entity.getAggregatedRows(),
                entity.getErrorMessage()
        );
    }

    private record FilingContext(
            String sourceFile,
            SubmissionRow submission,
            CoverPageRow coverPage,
            LocalDate reportPeriod
    ) {
    }

    private record ImportFileStart(
            Sec13fImportFile importFile,
            boolean forceAggregate
    ) {
    }

    private record PreparedHolding(
            HoldingRow row,
            SubmissionRow submission,
            LocalDate reportPeriod,
            Stock stock
    ) {
    }

    private record HoldingKey(
            String accessionNumber,
            Long stockId,
            String cusip
    ) {
        static HoldingKey from(Sec13fHolding holding) {
            return new HoldingKey(
                    holding.getAccessionNumber(),
                    holding.getStock().getStockId(),
                    holding.getCusip()
            );
        }
    }

    private record AggregateKey(
            Long stockId,
            LocalDate reportPeriod
    ) {
    }

    private static class UpsertCounters {
        private int createdFilings;
        private int updatedFilings;
        private int createdHoldings;
        private int updatedHoldings;
        private int unchangedHoldings;
        private int aggregatedRows;
    }

    private record ValueNormalization(
            String unit,
            BigDecimal marketValueUsd
    ) {
    }

    private record AggregatedPeriod(
            LocalDate reportPeriod,
            String cusip,
            int institutionCount,
            int filingRowCount,
            BigDecimal sharesHeld,
            BigDecimal marketValueUsd
    ) {
    }

    public record ImportDirectoryResult(
            String sourceDir,
            int fileCount,
            long successCount,
            long failedCount,
            List<ImportFileResult> files
    ) {
    }

    public record ImportFileResult(
            String sourceFile,
            String sourcePath,
            String status,
            Instant startedAt,
            Instant finishedAt,
            long submissionRows,
            long coverPageRows,
            long infoTableRows,
            long matchedInfoTableRows,
            long skippedUnmappedRows,
            long skippedDerivativeRows,
            long skippedNonShareRows,
            long parsedHoldingRows,
            int createdFilings,
            int updatedFilings,
            int createdHoldings,
            int updatedHoldings,
            int unchangedHoldings,
            int aggregatedRows,
            String errorMessage
    ) {
    }

    public record AggregateRebuildResult(
            int stockCount,
            int stockPeriodCount,
            int changedRows
    ) {
    }

    public record QuarterlyHoldingResult(
            String stockCode,
            LocalDate reportPeriod,
            String cusip,
            int institutionCount,
            int filingRowCount,
            BigDecimal sharesHeld,
            BigDecimal sharesChange,
            BigDecimal sharesChangeRate,
            BigDecimal marketValueUsd,
            BigDecimal sharesOutstanding,
            BigDecimal holdingRatio,
            String source
    ) {
        static QuarterlyHoldingResult from(StockInstitutionalHoldingQuarterly entity) {
            return new QuarterlyHoldingResult(
                    entity.getStockCode(),
                    entity.getReportPeriod(),
                    entity.getCusip(),
                    entity.getInstitutionCount(),
                    entity.getFilingRowCount(),
                    entity.getSharesHeld(),
                    entity.getSharesChange(),
                    entity.getSharesChangeRate(),
                    entity.getMarketValueUsd(),
                    entity.getSharesOutstanding(),
                    entity.getHoldingRatio(),
                    entity.getSource()
            );
        }
    }

    public record CusipMappingResult(
            Long id,
            String stockCode,
            String cusip,
            String issuerName,
            String source,
            int confidence,
            boolean active,
            boolean created
    ) {
    }
}
