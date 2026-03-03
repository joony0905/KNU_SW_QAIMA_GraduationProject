package com.qaima.service.stock;

import com.qaima.common.Blocking;
import com.qaima.common.CompanyNameNormalizer;
import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.Stock;
import com.qaima.domain.StockAlias;
import com.qaima.dto.stock.StockCodeMappingDto;
import com.qaima.repository.StockAliasRepository;
import com.qaima.repository.StockRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StockMappingService {

    private static final int SEARCH_LIMIT = 200;
    private static final int SEARCH_RESULT_LIMIT = 20;

    private final StockRepository stockRepository;
    private final StockAliasRepository stockAliasRepository;

    public Mono<StockCodeMappingDto> normalizeStockCodeByName(
            String companyName,
            String exchangeCode,
            String symbol
    ) {
        SymbolParts parts = parseSymbolIfPresent(symbol);
        if (parts != null) {
            return findMappingBySymbol(parts);
        }

        String normalizedName = CompanyNameNormalizer.normalizeKey(companyName);
        if (normalizedName.isBlank()) {
            return Mono.error(new IllegalArgumentException("companyName is required when symbol is empty"));
        }

        String normalizedExchange = normalizeExchangeFilter(exchangeCode);
        return fetchStocksByName(companyName, normalizedName, normalizedExchange)
                .flatMap(matches -> resolveSingleMatch(matches, companyName, normalizedExchange));
    }

    public Mono<List<StockCodeMappingDto>> listMappingsByName(
            String companyName,
            String exchangeCode,
            String symbol
    ) {
        SymbolParts parts = parseSymbolIfPresent(symbol);
        if (parts != null) {
            return listMappingsBySymbol(parts);
        }

        String normalizedName = CompanyNameNormalizer.normalizeKey(companyName);
        if (normalizedName.isBlank()) {
            return Mono.error(new IllegalArgumentException("companyName is required when symbol is empty"));
        }

        String normalizedExchange = normalizeExchangeFilter(exchangeCode);
        return fetchCandidatesByName(companyName, normalizedName, normalizedExchange)
                .map(matches -> matches.stream()
                        .map(this::toMappingDto)
                        .toList());
    }

    public Mono<List<StockCodeMappingDto>> searchStockMappings(String query) {
        String keyword = CompanyNameNormalizer.extractSearchKeyword(query);
        if (keyword.isBlank()) {
            return Mono.just(List.of());
        }

        String normalizedKey = CompanyNameNormalizer.normalizeKey(query);

        return Blocking.call(() -> {
            Map<Long, Stock> merged = new LinkedHashMap<>();

            List<Stock> fromName = stockRepository
                    .findTop20ByCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(keyword);
            for (Stock stock : fromName) {
                if (stock.getStockId() != null) {
                    merged.putIfAbsent(stock.getStockId(), stock);
                }
            }

            if (!normalizedKey.isBlank()) {
                List<StockAlias> aliases = stockAliasRepository.findByNormalizedAlias(normalizedKey);
                for (StockAlias alias : aliases) {
                    Stock stock = alias.getStock();
                    if (stock != null && stock.getStockId() != null) {
                        merged.putIfAbsent(stock.getStockId(), stock);
                    }
                }
            }

            return merged.values().stream()
                    .limit(SEARCH_RESULT_LIMIT)
                    .map(this::toMappingDto)
                    .toList();
        });
    }

    private StockCodeMappingDto toMappingDto(Stock stock) {
        if (stock == null) {
            return null;
        }

        String exchangeCode = stock.getExchange() != null ? stock.getExchange().getCode() : null;
        String stockCode = stock.getStockCode();
        return StockCodeMappingDto.builder()
                .stockCode(stockCode)
                .companyName(stock.getCompanyName())
                .exchangeCode(exchangeCode)
                .symbol(toSymbol(exchangeCode, stockCode))
                .build();
    }

    private Mono<List<Stock>> fetchStocksByName(
            String companyName,
            String normalizedKey,
            String exchangeCode
    ) {
        String keyword = CompanyNameNormalizer.extractSearchKeyword(companyName);
        if (keyword.isBlank()) {
            return Mono.just(List.of());
        }

        return Blocking.call(() -> {
            List<Stock> candidates = loadCandidates(keyword, exchangeCode);
            List<Stock> matches = filterByNormalizedKey(candidates, normalizedKey);
            if (!matches.isEmpty()) {
                return matches;
            }
            return findByAliasNormalized(normalizedKey, exchangeCode);
        });
    }

    private Mono<List<Stock>> fetchCandidatesByName(
            String companyName,
            String normalizedKey,
            String exchangeCode
    ) {
        String keyword = CompanyNameNormalizer.extractSearchKeyword(companyName);
        if (keyword.isBlank()) {
            return Mono.just(List.of());
        }

        return Blocking.call(() -> {
            List<Stock> candidates = loadCandidates(keyword, exchangeCode);
            List<Stock> matches = filterByNormalizedKey(candidates, normalizedKey);
            if (!matches.isEmpty()) {
                return limitCandidates(matches);
            }

            List<Stock> aliasMatches = findByAliasNormalized(normalizedKey, exchangeCode);
            if (!aliasMatches.isEmpty()) {
                return limitCandidates(aliasMatches);
            }

            return limitCandidates(candidates);
        });
    }

    private List<Stock> loadCandidates(String keyword, String exchangeCode) {
        PageRequest pageRequest = PageRequest.of(0, SEARCH_LIMIT);
        if (exchangeCode == null) {
            return stockRepository.findByCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(keyword, pageRequest);
        }

        return stockRepository.findByExchange_CodeIgnoreCaseAndCompanyNameContainingIgnoreCaseOrderByCompanyNameAsc(
                exchangeCode,
                keyword,
                pageRequest
        );
    }

    private List<Stock> filterByNormalizedKey(List<Stock> candidates, String normalizedKey) {
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return List.of();
        }

        return candidates.stream()
                .filter(stock -> normalizedKey.equals(CompanyNameNormalizer.normalizeKey(stock.getCompanyName())))
                .toList();
    }

    private List<Stock> findByAliasNormalized(String normalizedKey, String exchangeCode) {
        if (normalizedKey == null || normalizedKey.isBlank()) {
            return List.of();
        }

        List<StockAlias> aliases = stockAliasRepository.findByNormalizedAlias(normalizedKey);
        if (aliases.isEmpty()) {
            return List.of();
        }

        return aliases.stream()
                .map(StockAlias::getStock)
                .filter(stock -> stock != null)
                .filter(stock -> {
                    if (exchangeCode == null) {
                        return true;
                    }
                    if (stock.getExchange() == null || stock.getExchange().getCode() == null) {
                        return false;
                    }
                    return stock.getExchange().getCode().equalsIgnoreCase(exchangeCode);
                })
                .distinct()
                .toList();
    }

    private Mono<StockCodeMappingDto> resolveSingleMatch(
            List<Stock> matches,
            String companyName,
            String exchangeCode
    ) {
        if (matches.isEmpty()) {
            return Mono.error(new ResourceNotFoundException("Unknown companyName: " + companyName));
        }
        if (matches.size() > 1) {
            String suffix = exchangeCode == null ? "" : " (exchange=" + exchangeCode + ")";
            return Mono.error(new IllegalArgumentException("Ambiguous companyName: " + companyName + suffix));
        }
        return Mono.just(toMappingDto(matches.get(0)));
    }

    private Mono<StockCodeMappingDto> findMappingBySymbol(SymbolParts parts) {
        if (parts.exchangeCode() != null) {
            return Blocking.call(() ->
                            stockRepository.findByExchangeCodeAndStockCodeIgnoreCase(parts.exchangeCode(), parts.stockCode()))
                    .flatMap(optional -> optional
                            .map(stock -> Mono.just(toMappingDto(stock)))
                            .orElseGet(() -> Mono.error(new ResourceNotFoundException("Unknown symbol: " + parts.rawSymbol()))));
        }

        return Blocking.call(() -> stockRepository.findByStockCodeIgnoreCaseWithExchange(parts.stockCode()))
                .flatMap(matches -> {
                    if (matches.isEmpty()) {
                        return Mono.error(new ResourceNotFoundException("Unknown symbol: " + parts.rawSymbol()));
                    }
                    if (matches.size() > 1) {
                        return Mono.error(new IllegalArgumentException("Ambiguous symbol: " + parts.rawSymbol()));
                    }
                    return Mono.just(toMappingDto(matches.get(0)));
                });
    }

    private Mono<List<StockCodeMappingDto>> listMappingsBySymbol(SymbolParts parts) {
        if (parts.exchangeCode() != null) {
            return Blocking.call(() ->
                            stockRepository.findByExchangeCodeAndStockCodeIgnoreCase(parts.exchangeCode(), parts.stockCode()))
                    .map(optional -> optional
                            .map(stock -> List.of(toMappingDto(stock)))
                            .orElseGet(List::of));
        }

        return Blocking.call(() -> stockRepository.findByStockCodeIgnoreCaseWithExchange(parts.stockCode()))
                .map(matches -> matches.stream()
                        .map(this::toMappingDto)
                        .toList());
    }

    private List<Stock> limitCandidates(List<Stock> candidates) {
        if (candidates.size() <= SEARCH_RESULT_LIMIT) {
            return candidates;
        }
        return candidates.subList(0, SEARCH_RESULT_LIMIT);
    }

    private String toSymbol(String exchangeCode, String stockCode) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return null;
        }
        if (stockCode == null || stockCode.isBlank()) {
            return null;
        }
        return exchangeCode + ":" + stockCode;
    }

    private SymbolParts parseSymbolIfPresent(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        String trimmed = symbol.trim();
        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            String exchange = trimmed.substring(0, colon).trim();
            String stockCode = trimmed.substring(colon + 1).trim();
            if (exchange.isBlank() || stockCode.isBlank()) {
                throw new IllegalArgumentException("symbol must be EXCHANGE:CODE");
            }
            return new SymbolParts(normalizeExchangeFilter(exchange), stockCode, trimmed);
        }

        int dot = trimmed.indexOf('.');
        if (dot > 0 && dot < trimmed.length() - 1) {
            String stockCode = trimmed.substring(0, dot).trim();
            String exchange = trimmed.substring(dot + 1).trim();
            if (exchange.isBlank() || stockCode.isBlank()) {
                throw new IllegalArgumentException("symbol must be EXCHANGE:CODE");
            }
            return new SymbolParts(normalizeExchangeFilter(exchange), stockCode, trimmed);
        }

        throw new IllegalArgumentException("symbol must be EXCHANGE:CODE");
    }

    private String normalizeExchangeFilter(String exchangeCode) {
        if (exchangeCode == null) {
            return null;
        }

        String trimmed = exchangeCode.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        String normalized = trimmed.toUpperCase().replace(" ", "");
        return switch (normalized) {
            case "KOSPI" -> "KOSPI";
            case "KOSDAQ", "XKOS" -> "KOSDAQ";
            case "KONEX", "XKON" -> "KONEX";
            case "NASDAQ", "XNAS" -> "NASDAQ";
            case "NYSE", "XNYS" -> "NYSE";
            case "KRX", "XKRX", "KRXSM" -> null;
            default -> trimmed.toUpperCase();
        };
    }

    private record SymbolParts(String exchangeCode, String stockCode, String rawSymbol) {
    }
}
