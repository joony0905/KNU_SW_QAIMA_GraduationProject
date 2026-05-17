package com.qaima.service.issuedshares;

import com.qaima.common.Blocking;
import com.qaima.domain.IssuedShares;
import com.qaima.domain.Stock;
import com.qaima.dto.sec.SecIssuedSharesFact;
import com.qaima.external.SecCompanyFactsClient;
import com.qaima.repository.IssuedSharesRepository;
import com.qaima.repository.StockRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecIssuedSharesSyncService {

    public static final String SHARE_TYPE_COMMON = "COMMON";
    public static final String SOURCE = "SEC_EDGAR_COMPANYFACTS";

    private final SecCompanyFactsClient secCompanyFactsClient;
    private final IssuedSharesRepository issuedSharesRepository;
    private final StockRepository stockRepository;

    public Mono<BatchResult> syncAllMappedStocks(int limit) {
        return Blocking.call(() -> findSyncTargets(limit))
                .flatMap(targets -> syncStocks(targets.stocks(), targets.availableStocks()));
    }

    public Mono<StockSyncResult> syncForStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        return Blocking.call(() -> stockRepository.findByStockCodeWithExchange(stockCode.trim())
                        .orElseThrow(() -> new IllegalArgumentException("stock not found: " + stockCode)))
                .flatMap(this::syncForStock);
    }

    private SyncTargets findSyncTargets(int limit) {
        int availableStocks = toIntCount(stockRepository.countBySecCikIsNotNull());
        if (availableStocks == 0) {
            return new SyncTargets(0, List.of());
        }

        int targetLimit = limit > 0 ? Math.min(limit, availableStocks) : availableStocks;
        return new SyncTargets(
                availableStocks,
                stockRepository.findSecIssuedSharesSyncTargets(PageRequest.of(0, targetLimit))
        );
    }

    private Mono<BatchResult> syncStocks(List<Stock> stocks, int availableStocks) {
        List<Stock> safeStocks = stocks == null ? List.of() : stocks;
        int targetCount = safeStocks.size();

        int processedStocks = 0;
        int noDataStocks = 0;
        int failedStocks = 0;
        int createdRows = 0;
        int updatedRows = 0;
        int unchangedRows = 0;

        for (int i = 0; i < targetCount; i++) {
            Stock stock = safeStocks.get(i);
            try {
                StockSyncResult result = syncForStock(stock).block();
                processedStocks++;
                if (result == null || result.noData()) {
                    noDataStocks++;
                    continue;
                }
                createdRows += result.createdRows();
                updatedRows += result.updatedRows();
                unchangedRows += result.unchangedRows();
            } catch (Exception e) {
                failedStocks++;
                log.warn("[SEC] issued shares sync failed. stockCode={}, secCik={}, cause={}",
                        stock.getStockCode(), stock.getSecCik(), e.getMessage());
            }
        }

        BatchResult result = new BatchResult(
                availableStocks,
                targetCount,
                processedStocks,
                noDataStocks,
                failedStocks,
                createdRows,
                updatedRows,
                unchangedRows
        );
        log.info("[SEC] issued shares batch sync complete. availableStocks={}, targetStocks={}, processedStocks={}, noDataStocks={}, failedStocks={}, createdRows={}, updatedRows={}, unchangedRows={}",
                result.availableStocks(),
                result.targetStocks(),
                result.processedStocks(),
                result.noDataStocks(),
                result.failedStocks(),
                result.createdRows(),
                result.updatedRows(),
                result.unchangedRows());
        return Mono.just(result);
    }

    private Mono<StockSyncResult> syncForStock(Stock stock) {
        if (stock.getSecCik() == null || stock.getSecCik().isBlank()) {
            return Mono.error(new IllegalStateException("stock.secCik is missing: " + stock.getStockCode()));
        }

        return secCompanyFactsClient.fetchLatestCommonSharesOutstanding(stock.getSecCik())
                .flatMap(optionalFact -> {
                    if (optionalFact.isEmpty()) {
                        return Blocking.call(() -> {
                            markStockIssuedSharesSynced(stock, Instant.now());
                            return new StockSyncResult(
                                    stock.getStockCode(),
                                    stock.getSecCik(),
                                    null,
                                    null,
                                    null,
                                    true,
                                    0,
                                    0,
                                    0
                            );
                        });
                    }
                    return Blocking.call(() -> upsertFact(stock, optionalFact.get()));
                });
    }

    private StockSyncResult upsertFact(Stock stock, SecIssuedSharesFact fact) {
        IssuedShares entity = issuedSharesRepository
                .findByStockAndBaseDateAndShareType(stock, fact.endDate(), SHARE_TYPE_COMMON)
                .orElseGet(IssuedShares::new);
        boolean isNew = entity.getIssuedSharesId() == null;

        boolean changed = isNew
                || !Objects.equals(entity.getRceptNo(), fact.accessionNumber())
                || !Objects.equals(entity.getCorpCls(), "SEC")
                || !Objects.equals(entity.getCorpCode(), fact.cik())
                || !Objects.equals(entity.getCorpName(), fact.entityName())
                || !Objects.equals(entity.getShareType(), SHARE_TYPE_COMMON)
                || !Objects.equals(entity.getIssuedSharesToDate(), fact.sharesOutstanding())
                || !Objects.equals(entity.getIssuedSharesTotal(), fact.sharesOutstanding())
                || !Objects.equals(entity.getBaseDate(), fact.endDate())
                || !Objects.equals(entity.getSource(), SOURCE);

        if (!changed) {
            markStockIssuedSharesSynced(stock, Instant.now());
            return toResult(stock, fact, false, 0, 0, 1);
        }

        entity.setStock(stock);
        entity.setRceptNo(requireText(fact.accessionNumber(), "accessionNumber"));
        entity.setCorpCls("SEC");
        entity.setCorpCode(requireText(fact.cik(), "cik"));
        entity.setCorpName(fact.entityName());
        entity.setShareType(SHARE_TYPE_COMMON);
        entity.setAuthorizedShares(null);
        entity.setIssuedSharesToDate(fact.sharesOutstanding());
        entity.setDecreasedSharesToDate(null);
        entity.setDecreasedByReduction(null);
        entity.setDecreasedByProfitIncineration(null);
        entity.setDecreasedByRedemption(null);
        entity.setDecreasedByOther(null);
        entity.setIssuedSharesTotal(fact.sharesOutstanding());
        entity.setTreasuryShares(null);
        entity.setFloatingShares(null);
        entity.setBaseDate(fact.endDate());
        entity.setSource(SOURCE);

        issuedSharesRepository.save(entity);
        markStockIssuedSharesSynced(stock, Instant.now());
        return toResult(stock, fact, false, isNew ? 1 : 0, isNew ? 0 : 1, 0);
    }

    private void markStockIssuedSharesSynced(Stock stock, Instant syncedAt) {
        stock.setSecIssuedSharesSyncedAt(syncedAt);
        stockRepository.save(stock);
    }

    private StockSyncResult toResult(
            Stock stock,
            SecIssuedSharesFact fact,
            boolean noData,
            int createdRows,
            int updatedRows,
            int unchangedRows
    ) {
        return new StockSyncResult(
                stock.getStockCode(),
                fact.cik(),
                fact.endDate(),
                fact.accessionNumber(),
                fact.sharesOutstanding(),
                noData,
                createdRows,
                updatedRows,
                unchangedRows
        );
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("SEC issued shares " + fieldName + " is required");
        }
        return value.trim();
    }

    private int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }

    private record SyncTargets(
            int availableStocks,
            List<Stock> stocks
    ) {
    }

    public record StockSyncResult(
            String stockCode,
            String cik,
            java.time.LocalDate baseDate,
            String accessionNumber,
            Long sharesOutstanding,
            boolean noData,
            int createdRows,
            int updatedRows,
            int unchangedRows
    ) {
    }

    public record BatchResult(
            int availableStocks,
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
