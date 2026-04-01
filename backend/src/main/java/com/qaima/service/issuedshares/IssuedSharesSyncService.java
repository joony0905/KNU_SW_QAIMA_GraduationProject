package com.qaima.service.issuedshares;

import com.qaima.common.Blocking;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.Stock;
import com.qaima.dto.opendart.OpenDartStockTotalStatusResponse;
import com.qaima.external.OpenDartClient;
import com.qaima.repository.IssuedSharesRepository;
import com.qaima.repository.StockRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssuedSharesSyncService {

    public static final String REPORT_CODE_Q1 = "11013";
    public static final String REPORT_CODE_HALF = "11012";
    public static final String REPORT_CODE_Q3 = "11014";
    public static final String REPORT_CODE_ANNUAL = "11011";
    public static final String DEFAULT_SOURCE = "OPENDART_STOCK_TOTQY";

    private final OpenDartClient openDartClient;
    private final IssuedSharesRepository issuedSharesRepository;
    private final StockRepository stockRepository;

    public Mono<DailyBatchResult> syncDailyAllMappedStocks() {
        return Blocking.call(this::syncDailyAllMappedStocksInternal);
    }

    public Mono<StockSyncResult> syncDailyForStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        return Blocking.call(() -> stockRepository.findByStockCodeWithExchange(stockCode.trim())
                        .orElseThrow(() -> new IllegalArgumentException("stock not found: " + stockCode)))
                .flatMap(stock -> Blocking.call(() -> syncDailyForStockInternal(stock, LocalDate.now())));
    }

    public Mono<IssuedShares> findLatest(Stock stock) {
        if (stock == null || stock.getStockId() == null) {
            return Mono.empty();
        }

        return Blocking.call(() -> issuedSharesRepository.findTopByStockOrderByBaseDateDesc(stock).orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    public Mono<IssuedShares> findLatest(Stock stock, String shareType) {
        if (stock == null || stock.getStockId() == null || shareType == null || shareType.isBlank()) {
            return Mono.empty();
        }

        return Blocking.call(() -> issuedSharesRepository
                        .findTopByStockAndShareTypeOrderByBaseDateDesc(stock, shareType.trim())
                        .orElse(null))
                .flatMap(Mono::justOrEmpty);
    }

    private DailyBatchResult syncDailyAllMappedStocksInternal() {
        List<Stock> stocks = stockRepository.findByDartCorpCodeIsNotNullOrderByStockCodeAsc();
        LocalDate asOfDate = LocalDate.now();

        int processedStocks = 0;
        int noDataStocks = 0;
        int failedStocks = 0;
        int createdRows = 0;
        int updatedRows = 0;
        int unchangedRows = 0;

        for (Stock stock : stocks) {
            try {
                StockSyncResult result = syncDailyForStockInternal(stock, asOfDate);
                processedStocks++;
                createdRows += result.createdRows();
                updatedRows += result.updatedRows();
                unchangedRows += result.unchangedRows();
                if (result.noData()) {
                    noDataStocks++;
                }
            } catch (Exception e) {
                failedStocks++;
                log.warn("[IssuedSharesSync] daily sync failed. stockCode={}, corpCode={}, cause={}",
                        stock.getStockCode(), stock.getDartCorpCode(), e.getMessage());
            }
        }

        DailyBatchResult result = new DailyBatchResult(
                stocks.size(),
                processedStocks,
                noDataStocks,
                failedStocks,
                createdRows,
                updatedRows,
                unchangedRows
        );

        log.info("[IssuedSharesSync] daily batch complete. targetStocks={}, processedStocks={}, noDataStocks={}, failedStocks={}, createdRows={}, updatedRows={}, unchangedRows={}",
                result.targetStocks(),
                result.processedStocks(),
                result.noDataStocks(),
                result.failedStocks(),
                result.createdRows(),
                result.updatedRows(),
                result.unchangedRows());

        return result;
    }

    private StockSyncResult syncDailyForStockInternal(Stock stock, LocalDate asOfDate) {
        if (stock.getDartCorpCode() == null || stock.getDartCorpCode().isBlank()) {
            throw new IllegalStateException("stock.dartCorpCode is missing: " + stock.getStockCode());
        }

        int checkedReports = 0;

        for (ReportTarget target : resolveDailyReportTargets(asOfDate)) {
            checkedReports++;
            List<OpenDartStockTotalStatusResponse.Row> rows = openDartClient
                    .fetchStockTotalStatus(stock.getDartCorpCode(), target.businessYear(), target.reportCode())
                    .block();

            if (rows == null || rows.isEmpty()) {
                continue;
            }
            if (!hasMeaningfulRows(rows)) {
                log.info("[IssuedSharesSync] skip report without usable issued shares. stockCode={}, reportYear={}, reportCode={}",
                        stock.getStockCode(), target.businessYear(), target.reportCode());
                continue;
            }

            UpsertSummary summary = upsertRows(stock, stock.getDartCorpCode(), rows);
            StockSyncResult result = new StockSyncResult(
                    stock.getStockCode(),
                    stock.getDartCorpCode(),
                    target.businessYear(),
                    target.reportCode(),
                    checkedReports,
                    false,
                    summary.createdRows(),
                    summary.updatedRows(),
                    summary.unchangedRows()
            );

            log.info("[IssuedSharesSync] daily stock sync complete. stockCode={}, reportYear={}, reportCode={}, checkedReports={}, createdRows={}, updatedRows={}, unchangedRows={}",
                    result.stockCode(),
                    result.businessYear(),
                    result.reportCode(),
                    result.checkedReports(),
                    result.createdRows(),
                    result.updatedRows(),
                    result.unchangedRows());

            return result;
        }

        return new StockSyncResult(
                stock.getStockCode(),
                stock.getDartCorpCode(),
                null,
                null,
                checkedReports,
                true,
                0,
                0,
                0
        );
    }

    private List<ReportTarget> resolveDailyReportTargets(LocalDate asOfDate) {
        int year = asOfDate.getYear();
        List<ReportTarget> targets = new ArrayList<>();

        if (!asOfDate.isBefore(LocalDate.of(year, 11, 15))) {
            targets.add(new ReportTarget(year, REPORT_CODE_Q3));
        }
        if (!asOfDate.isBefore(LocalDate.of(year, 8, 15))) {
            targets.add(new ReportTarget(year, REPORT_CODE_HALF));
        }
        if (!asOfDate.isBefore(LocalDate.of(year, 5, 15))) {
            targets.add(new ReportTarget(year, REPORT_CODE_Q1));
        }
        if (!asOfDate.isBefore(LocalDate.of(year, 3, 31))) {
            targets.add(new ReportTarget(year - 1, REPORT_CODE_ANNUAL));
        }

        targets.add(new ReportTarget(year - 1, REPORT_CODE_Q3));
        targets.add(new ReportTarget(year - 1, REPORT_CODE_HALF));
        targets.add(new ReportTarget(year - 1, REPORT_CODE_Q1));
        targets.add(new ReportTarget(year - 2, REPORT_CODE_ANNUAL));

        return targets;
    }

    private UpsertSummary upsertRows(
            Stock stock,
            String corpCode,
            List<OpenDartStockTotalStatusResponse.Row> rows
    ) {
        int createdRows = 0;
        int updatedRows = 0;
        int unchangedRows = 0;

        for (OpenDartStockTotalStatusResponse.Row row : rows) {
            if (!hasMeaningfulRow(row)) {
                continue;
            }

            String shareType = requireText(row.getSe(), "se");
            LocalDate baseDate = parseDate(row.getStlmDt(), "stlmDt");

            IssuedShares entity = issuedSharesRepository
                    .findByStockAndBaseDateAndShareType(stock, baseDate, shareType)
                    .orElseGet(IssuedShares::new);

            boolean isNew = entity.getIssuedSharesId() == null;

            String resolvedCorpCode = requireText(firstNonBlank(row.getCorpCode(), corpCode), "corpCode");
            String rceptNo = requireText(row.getRceptNo(), "rceptNo");
            String corpCls = trimToNull(row.getCorpCls());
            String corpName = trimToNull(row.getCorpName());
            Long authorizedShares = parseLong(row.getIsuStockTotqy());
            Long issuedSharesToDate = parseLong(row.getNowToIsuStockTotqy());
            Long decreasedSharesToDate = parseLong(row.getNowToDcrsStockTotqy());
            Long decreasedByReduction = parseLong(row.getRedc());
            Long decreasedByProfitIncineration = parseLong(row.getProfitIncnr());
            Long decreasedByRedemption = parseLong(row.getRdmstkRepy());
            Long decreasedByOther = parseLong(row.getEtc());
            Long issuedSharesTotal = parseLong(row.getIstcTotqy());
            Long treasuryShares = parseLong(row.getTesstkCo());
            Long floatingShares = parseLong(row.getDistbStockCo());

            boolean changed = isNew
                    || !Objects.equals(entity.getRceptNo(), rceptNo)
                    || !Objects.equals(entity.getCorpCls(), corpCls)
                    || !Objects.equals(entity.getCorpCode(), resolvedCorpCode)
                    || !Objects.equals(entity.getCorpName(), corpName)
                    || !Objects.equals(entity.getShareType(), shareType)
                    || !Objects.equals(entity.getAuthorizedShares(), authorizedShares)
                    || !Objects.equals(entity.getIssuedSharesToDate(), issuedSharesToDate)
                    || !Objects.equals(entity.getDecreasedSharesToDate(), decreasedSharesToDate)
                    || !Objects.equals(entity.getDecreasedByReduction(), decreasedByReduction)
                    || !Objects.equals(entity.getDecreasedByProfitIncineration(), decreasedByProfitIncineration)
                    || !Objects.equals(entity.getDecreasedByRedemption(), decreasedByRedemption)
                    || !Objects.equals(entity.getDecreasedByOther(), decreasedByOther)
                    || !Objects.equals(entity.getIssuedSharesTotal(), issuedSharesTotal)
                    || !Objects.equals(entity.getTreasuryShares(), treasuryShares)
                    || !Objects.equals(entity.getFloatingShares(), floatingShares)
                    || !Objects.equals(entity.getBaseDate(), baseDate)
                    || !Objects.equals(entity.getSource(), DEFAULT_SOURCE);

            if (!changed) {
                unchangedRows++;
                continue;
            }

            entity.setStock(stock);
            entity.setRceptNo(rceptNo);
            entity.setCorpCls(corpCls);
            entity.setCorpCode(resolvedCorpCode);
            entity.setCorpName(corpName);
            entity.setShareType(shareType);
            entity.setAuthorizedShares(authorizedShares);
            entity.setIssuedSharesToDate(issuedSharesToDate);
            entity.setDecreasedSharesToDate(decreasedSharesToDate);
            entity.setDecreasedByReduction(decreasedByReduction);
            entity.setDecreasedByProfitIncineration(decreasedByProfitIncineration);
            entity.setDecreasedByRedemption(decreasedByRedemption);
            entity.setDecreasedByOther(decreasedByOther);
            entity.setIssuedSharesTotal(issuedSharesTotal);
            entity.setTreasuryShares(treasuryShares);
            entity.setFloatingShares(floatingShares);
            entity.setBaseDate(baseDate);
            entity.setSource(DEFAULT_SOURCE);

            issuedSharesRepository.save(entity);

            if (isNew) {
                createdRows++;
            } else {
                updatedRows++;
            }
        }

        return new UpsertSummary(createdRows, updatedRows, unchangedRows);
    }

    private boolean hasMeaningfulRows(List<OpenDartStockTotalStatusResponse.Row> rows) {
        return rows.stream().anyMatch(this::hasMeaningfulRow);
    }

    private boolean hasMeaningfulRow(OpenDartStockTotalStatusResponse.Row row) {
        if (row == null) {
            return false;
        }

        return parseLong(row.getIstcTotqy()) != null
                || parseLong(row.getNowToIsuStockTotqy()) != null
                || parseLong(row.getTesstkCo()) != null
                || parseLong(row.getDistbStockCo()) != null;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("issued shares " + fieldName + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(String value, String fieldName) {
        String normalized = requireText(value, fieldName);
        return LocalDate.parse(normalized);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.replace(",", "").trim();
        if (normalized.isEmpty() || "-".equals(normalized)) {
            return null;
        }

        return Long.parseLong(normalized);
    }

    private record ReportTarget(int businessYear, String reportCode) {
    }

    private record UpsertSummary(int createdRows, int updatedRows, int unchangedRows) {
    }

    public record StockSyncResult(
            String stockCode,
            String corpCode,
            Integer businessYear,
            String reportCode,
            int checkedReports,
            boolean noData,
            int createdRows,
            int updatedRows,
            int unchangedRows
    ) {
    }

    public record DailyBatchResult(
            int targetStocks,
            int processedStocks,
            int noDataStocks,
            int failedStocks,
            int createdRows,
            int updatedRows,
            int unchangedRows
    ) {
    }
}
