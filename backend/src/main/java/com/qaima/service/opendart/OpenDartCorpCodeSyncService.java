package com.qaima.service.opendart;

import com.qaima.common.Blocking;
import com.qaima.domain.ShareClass;
import com.qaima.domain.Stock;
import com.qaima.external.OpenDartClient;
import com.qaima.repository.StockRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenDartCorpCodeSyncService {

    private final OpenDartClient openDartClient;
    private final StockRepository stockRepository;

    public Mono<SyncResult> syncAllStockMappings() {
        return openDartClient.fetchCorpCodes()
                .flatMap(entries -> Blocking.call(() -> syncEntries(entries)));
    }

    private SyncResult syncEntries(List<OpenDartClient.CorpCodeEntry> entries) {
        Map<String, OpenDartClient.CorpCodeEntry> entryByStockCode = new HashMap<>();
        Map<String, OpenDartClient.CorpCodeEntry> fallbackEntryByKey = new HashMap<>();
        Set<String> ambiguousFallbackKeys = new HashSet<>();
        int listedEntries = 0;

        for (OpenDartClient.CorpCodeEntry entry : entries) {
            String stockCode = normalizeStockCode(entry.stockCode());
            if (stockCode == null) {
                continue;
            }
            listedEntries++;
            entryByStockCode.put(stockCode, entry);
        }

        List<Stock> stocks = stockRepository.findAllByOrderByStockCodeAsc();
        for (Stock stock : stocks) {
            String stockCode = normalizeStockCode(stock.getStockCode());
            OpenDartClient.CorpCodeEntry entry = entryByStockCode.get(stockCode);
            if (entry == null) {
                continue;
            }
            registerFallbackEntry(stock, stockCode, entry, fallbackEntryByKey, ambiguousFallbackKeys);
        }

        List<Stock> stocksToSave = new java.util.ArrayList<>();
        int matchedStocks = 0;
        int directMatchedStocks = 0;
        int fallbackMatchedStocks = 0;
        int updatedStocks = 0;
        int unchangedStocks = 0;
        int missingStocks = 0;
        Instant now = Instant.now();

        for (Stock stock : stocks) {
            String stockCode = normalizeStockCode(stock.getStockCode());
            OpenDartClient.CorpCodeEntry entry = entryByStockCode.get(stockCode);
            boolean fallbackMatched = false;
            ShareClass targetShareClass = ShareClass.OTHER;
            if (entry == null) {
                boolean changed = false;
                if (stock.getDartCorpCode() != null) {
                    stock.setDartCorpCode(null);
                    changed = true;
                }
                if (stock.getDartCorpName() != null) {
                    stock.setDartCorpName(null);
                    changed = true;
                }
                if (stock.getDartModifiedDate() != null) {
                    stock.setDartModifiedDate(null);
                    changed = true;
                }
                entry = resolveFallbackEntry(stock, stockCode, fallbackEntryByKey, ambiguousFallbackKeys);
                if (entry == null) {
                    if (stock.getShareClass() != targetShareClass) {
                        stock.setShareClass(targetShareClass);
                        changed = true;
                    }
                    if (changed) {
                        stock.setDartSyncedAt(now);
                        stocksToSave.add(stock);
                    }
                    missingStocks++;
                    continue;
                }
                fallbackMatched = true;
                targetShareClass = ShareClass.PREFERRED;
            } else {
                targetShareClass = ShareClass.COMMON;
            }

            matchedStocks++;
            if (fallbackMatched) {
                fallbackMatchedStocks++;
            } else {
                directMatchedStocks++;
            }
            boolean changed = false;

            if (!same(stock.getDartCorpCode(), entry.corpCode())) {
                stock.setDartCorpCode(entry.corpCode());
                changed = true;
            }
            if (!same(stock.getDartCorpName(), entry.corpName())) {
                stock.setDartCorpName(entry.corpName());
                changed = true;
            }
            if (!same(stock.getDartModifiedDate(), entry.modifyDate())) {
                stock.setDartModifiedDate(entry.modifyDate());
                changed = true;
            }
            if (stock.getShareClass() != targetShareClass) {
                stock.setShareClass(targetShareClass);
                changed = true;
            }

            stock.setDartSyncedAt(now);

            stocksToSave.add(stock);
            if (changed) {
                updatedStocks++;
            } else {
                unchangedStocks++;
            }
        }

        stockRepository.saveAll(stocksToSave);

        SyncResult result = new SyncResult(
                entries.size(),
                listedEntries,
                matchedStocks,
                updatedStocks,
                unchangedStocks,
                missingStocks,
                directMatchedStocks,
                fallbackMatchedStocks
        );

        log.info("[OpenDART] corp code sync complete. totalEntries={}, listedEntries={}, matchedStocks={}, directMatchedStocks={}, fallbackMatchedStocks={}, updatedStocks={}, unchangedStocks={}, missingStocks={}",
                result.totalEntries(),
                result.listedEntries(),
                result.matchedStocks(),
                result.directMatchedStocks(),
                result.fallbackMatchedStocks(),
                result.updatedStocks(),
                result.unchangedStocks(),
                result.missingStocks());

        return result;
    }

    private void registerFallbackEntry(
            Stock stock,
            String stockCode,
            OpenDartClient.CorpCodeEntry entry,
            Map<String, OpenDartClient.CorpCodeEntry> fallbackEntryByKey,
            Set<String> ambiguousFallbackKeys
    ) {
        String fallbackKey = fallbackKey(stock, stockCode);
        if (fallbackKey == null || ambiguousFallbackKeys.contains(fallbackKey)) {
            return;
        }

        OpenDartClient.CorpCodeEntry existing = fallbackEntryByKey.get(fallbackKey);
        if (existing == null) {
            fallbackEntryByKey.put(fallbackKey, entry);
            return;
        }

        if (!same(existing.corpCode(), entry.corpCode())) {
            fallbackEntryByKey.remove(fallbackKey);
            ambiguousFallbackKeys.add(fallbackKey);
        }
    }

    private OpenDartClient.CorpCodeEntry resolveFallbackEntry(
            Stock stock,
            String stockCode,
            Map<String, OpenDartClient.CorpCodeEntry> fallbackEntryByKey,
            Set<String> ambiguousFallbackKeys
    ) {
        String fallbackKey = fallbackKey(stock, stockCode);
        if (fallbackKey == null || ambiguousFallbackKeys.contains(fallbackKey)) {
            return null;
        }
        return fallbackEntryByKey.get(fallbackKey);
    }

    private String fallbackKey(Stock stock, String stockCode) {
        if (stock == null || stock.getExchange() == null || stockCode == null || stockCode.length() < 5) {
            return null;
        }
        if (!"KR".equalsIgnoreCase(stock.getExchange().getCountry())) {
            return null;
        }
        if (!"EQUITY".equalsIgnoreCase(stock.getAssetType())) {
            return null;
        }
        return stock.getExchange().getCode().toUpperCase() + ":" + stockCode.substring(0, 5);
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        String normalized = stockCode.trim().toUpperCase();
        if (normalized.matches("\\d+")) {
            return String.format("%06d", Integer.parseInt(normalized));
        }
        return normalized;
    }

    private boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    public record SyncResult(
            int totalEntries,
            int listedEntries,
            int matchedStocks,
            int updatedStocks,
            int unchangedStocks,
            int missingStocks,
            int directMatchedStocks,
            int fallbackMatchedStocks
    ) {
    }
}
