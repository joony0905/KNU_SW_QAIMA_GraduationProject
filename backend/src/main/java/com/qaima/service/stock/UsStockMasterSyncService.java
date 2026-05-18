package com.qaima.service.stock;

import com.qaima.common.Blocking;
import com.qaima.common.CompanyNameNormalizer;
import com.qaima.domain.Exchange;
import com.qaima.domain.Stock;
import com.qaima.domain.StockAlias;
import com.qaima.dto.sec.SecCompanyTickerExchangeEntry;
import com.qaima.external.SecCompanyTickerExchangeClient;
import com.qaima.repository.ExchangeRepository;
import com.qaima.repository.StockAliasRepository;
import com.qaima.repository.StockRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsStockMasterSyncService {

    private static final String ASSET_TYPE_EQUITY = "EQUITY";
    private static final String CURRENCY_USD = "USD";

    private final SecCompanyTickerExchangeClient secClient;
    private final ExchangeRepository exchangeRepository;
    private final StockRepository stockRepository;
    private final StockAliasRepository stockAliasRepository;
    private final PlatformTransactionManager transactionManager;

    public Mono<SyncResult> syncNasdaqNyseFromSec() {
        return secClient.fetchCompanyTickerExchange()
                .flatMap(entries -> Blocking.call(() -> syncEntries(entries)));
    }

    private SyncResult syncEntries(List<SecCompanyTickerExchangeEntry> entries) {
        List<SecCompanyTickerExchangeEntry> safeEntries = entries == null ? List.of() : entries;
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx.execute(status -> syncEntriesInTransaction(safeEntries));
    }

    private SyncResult syncEntriesInTransaction(List<SecCompanyTickerExchangeEntry> entries) {
        Exchange nasdaq = loadExchange("NASDAQ");
        Exchange nyse = loadExchange("NYSE");
        Map<String, Exchange> exchangeByCode = Map.of(
                "NASDAQ", nasdaq,
                "NYSE", nyse
        );
        Map<String, Stock> stockByListing = loadExistingUsStocks();
        Map<Long, Set<String>> aliasesByStockId = loadExistingUsAliases();
        Instant now = Instant.now();

        int eligibleEntries = 0;
        int createdStocks = 0;
        int updatedStocks = 0;
        int unchangedStocks = 0;
        int skippedExchange = 0;
        int skippedInvalid = 0;
        int aliasesCreated = 0;

        for (SecCompanyTickerExchangeEntry entry : entries) {
            String exchangeCode = normalizeExchange(entry.exchange());
            if (exchangeCode == null) {
                skippedExchange++;
                continue;
            }

            String ticker = normalizeTicker(entry.ticker());
            String companyName = normalizeName(entry.companyName());
            if (ticker == null || companyName == null) {
                skippedInvalid++;
                continue;
            }

            eligibleEntries++;
            String key = listingKey(exchangeCode, ticker);
            Stock stock = stockByListing.get(key);
            boolean created = false;
            boolean changed = false;

            if (stock == null) {
                stock = new Stock();
                stock.setExchange(exchangeByCode.get(exchangeCode));
                stock.setStockCode(ticker);
                stock.setCompanyName(companyName);
                stock.setCurrency(CURRENCY_USD);
                stock.setAssetType(ASSET_TYPE_EQUITY);
                created = true;
                changed = true;
            } else {
                changed |= setIfDifferentCompanyName(stock, companyName);
                changed |= setIfDifferentAssetType(stock, ASSET_TYPE_EQUITY);
                changed |= setIfDifferentCurrency(stock, CURRENCY_USD);
            }

            changed |= setIfDifferentSecCik(stock, entry.cik());
            changed |= setIfDifferentSecCompanyName(stock, companyName);
            stock.setSecSyncedAt(now);

            Stock saved = stockRepository.save(stock);
            stockByListing.put(key, saved);

            aliasesCreated += ensureAlias(saved, ticker, aliasesByStockId);
            aliasesCreated += ensureAlias(saved, companyName, aliasesByStockId);

            if (created) {
                createdStocks++;
            } else if (changed) {
                updatedStocks++;
            } else {
                unchangedStocks++;
            }
        }

        SyncResult result = new SyncResult(
                entries.size(),
                eligibleEntries,
                createdStocks,
                updatedStocks,
                unchangedStocks,
                skippedExchange,
                skippedInvalid,
                aliasesCreated
        );
        log.info("[SEC] US stock master sync complete. totalEntries={}, eligibleEntries={}, createdStocks={}, updatedStocks={}, unchangedStocks={}, skippedExchange={}, skippedInvalid={}, aliasesCreated={}",
                result.totalEntries(),
                result.eligibleEntries(),
                result.createdStocks(),
                result.updatedStocks(),
                result.unchangedStocks(),
                result.skippedExchange(),
                result.skippedInvalid(),
                result.aliasesCreated());
        return result;
    }

    private Map<String, Stock> loadExistingUsStocks() {
        Map<String, Stock> stocks = new HashMap<>();
        for (String exchange : List.of("NASDAQ", "NYSE")) {
            for (Stock stock : stockRepository.findByExchange_CodeIgnoreCaseOrderByStockCodeAsc(exchange)) {
                String ticker = normalizeTicker(stock.getStockCode());
                if (ticker != null) {
                    stocks.put(listingKey(exchange, ticker), stock);
                }
            }
        }
        return stocks;
    }

    private Map<Long, Set<String>> loadExistingUsAliases() {
        Map<Long, Set<String>> aliases = new HashMap<>();
        for (StockAlias alias : stockAliasRepository.findByStockExchangeCodes(List.of("NASDAQ", "NYSE"))) {
            if (alias.getStock() == null || alias.getStock().getStockId() == null) {
                continue;
            }
            if (alias.getNormalizedAlias() == null || alias.getNormalizedAlias().isBlank()) {
                continue;
            }
            aliases.computeIfAbsent(alias.getStock().getStockId(), ignored -> new HashSet<>())
                    .add(alias.getNormalizedAlias());
        }
        return aliases;
    }

    private Exchange loadExchange(String code) {
        return exchangeRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("DB exchange is missing: " + code));
    }

    private int ensureAlias(Stock stock, String aliasName, Map<Long, Set<String>> aliasesByStockId) {
        if (stock == null || stock.getStockId() == null || aliasName == null || aliasName.isBlank()) {
            return 0;
        }

        String normalizedAlias = CompanyNameNormalizer.normalizeKey(aliasName);
        if (normalizedAlias.isBlank()) {
            return 0;
        }

        Set<String> aliases = aliasesByStockId.computeIfAbsent(stock.getStockId(), ignored -> new HashSet<>());
        if (!aliases.add(normalizedAlias)) {
            return 0;
        }

        StockAlias alias = new StockAlias();
        alias.setStock(stock);
        alias.setAliasName(aliasName.trim());
        stockAliasRepository.save(alias);
        return 1;
    }

    private String normalizeExchange(String exchange) {
        if (exchange == null || exchange.isBlank()) {
            return null;
        }

        String normalized = exchange.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        return switch (normalized) {
            case "NASDAQ", "XNAS" -> "NASDAQ";
            case "NYSE", "XNYS" -> "NYSE";
            default -> null;
        };
    }

    private String normalizeTicker(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return null;
        }
        return ticker.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }

    private String listingKey(String exchangeCode, String ticker) {
        return exchangeCode + ":" + ticker;
    }

    private boolean setIfDifferentCompanyName(Stock stock, String value) {
        if (same(stock.getCompanyName(), value)) {
            return false;
        }
        stock.setCompanyName(value);
        return true;
    }

    private boolean setIfDifferentAssetType(Stock stock, String value) {
        if (same(stock.getAssetType(), value)) {
            return false;
        }
        stock.setAssetType(value);
        return true;
    }

    private boolean setIfDifferentCurrency(Stock stock, String value) {
        if (same(stock.getCurrency(), value)) {
            return false;
        }
        stock.setCurrency(value);
        return true;
    }

    private boolean setIfDifferentSecCik(Stock stock, String value) {
        if (same(stock.getSecCik(), value)) {
            return false;
        }
        stock.setSecCik(value);
        return true;
    }

    private boolean setIfDifferentSecCompanyName(Stock stock, String value) {
        if (same(stock.getSecCompanyName(), value)) {
            return false;
        }
        stock.setSecCompanyName(value);
        return true;
    }

    private boolean same(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }

    public record SyncResult(
            int totalEntries,
            int eligibleEntries,
            int createdStocks,
            int updatedStocks,
            int unchangedStocks,
            int skippedExchange,
            int skippedInvalid,
            int aliasesCreated
    ) {
    }
}
