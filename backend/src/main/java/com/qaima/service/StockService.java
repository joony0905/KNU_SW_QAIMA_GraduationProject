package com.qaima.service;

import com.qaima.common.exception.ResourceNotFoundException;
import com.qaima.domain.Stock;
import com.qaima.dto.StockDto;
import com.qaima.external.StockClient;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockClient stockClient;

    public Mono<StockDto> getStockWithRealtime(Long stockId) {
        return Mono.fromCallable(() ->
                        stockRepository.findById(stockId)
                                .orElseThrow(() ->
                                        new IllegalArgumentException("議댁옱?섏? ?딅뒗 醫낅ぉ ID: " + stockId)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stockClient::fetchStock);
    }

    public Mono<StockDto> getStockWithRealtimeByCode(String rawStockCode) {
        return getStockWithRealtimeByCode(rawStockCode, null);
    }

    public Mono<StockDto> getStockWithRealtimeByCode(String rawStockCode, String exchangeCode) {
        return loadStockMono(rawStockCode, exchangeCode)
                .flatMap(stockClient::fetchStock);
    }

    public Mono<Stock> getStockByCode(String rawStockCode) {
        return getStockByCode(rawStockCode, null);
    }

    public Mono<Stock> getStockByCode(String rawStockCode, String exchangeCode) {
        return loadStockMono(rawStockCode, exchangeCode);
    }

    private Mono<Stock> loadStockMono(String rawStockCode, String exchangeCode) {
        StockKey key = resolveStockKey(rawStockCode, exchangeCode);
        String normalizedCode = key.stockCode();
        String resolvedExchange = key.exchangeCode();

        if (normalizedCode == null || normalizedCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        return Mono.fromCallable(() -> {
                    if (resolvedExchange != null) {
                        return stockRepository
                                .findByExchangeCodeAndStockCodeIgnoreCase(resolvedExchange, normalizedCode)
                                .map(List::of)
                                .orElseGet(List::of);
                    }
                    return stockRepository.findByStockCodeIgnoreCaseWithExchange(normalizedCode);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(matches -> {
                    if (matches.size() == 1) {
                        return Mono.just(matches.get(0));
                    }
                    if (matches.size() > 1) {
                        return Mono.error(new IllegalArgumentException(
                                "Ambiguous stockCode: " + normalizedCode + " (provide exchange)"
                        ));
                    }

                    String exchangeLabel = (resolvedExchange == null) ? "unspecified" : resolvedExchange;
                    return Mono.error(new ResourceNotFoundException(
                            "Unknown stockCode: " + normalizedCode + " (exchange=" + exchangeLabel + ")"
                    ));
                });
    }

    private StockKey resolveStockKey(String rawStockCode, String exchangeCode) {
        StockKey symbol = parseSymbol(rawStockCode);
        if (symbol != null) {
            return symbol;
        }
        return new StockKey(normalizeStockCode(rawStockCode), resolveExchangeCode(exchangeCode));
    }

    private StockKey parseSymbol(String rawStockCode) {
        if (rawStockCode == null) return null;
        String trimmed = rawStockCode.trim();
        if (trimmed.isEmpty()) return null;

        int colon = trimmed.indexOf(':');
        if (colon > 0 && colon < trimmed.length() - 1) {
            String exchange = trimmed.substring(0, colon).trim();
            String stockCode = trimmed.substring(colon + 1).trim();
            String normalizedExchange = normalizeExchangeCode(exchange);
            if (!stockCode.isBlank() && isSupportedExchange(normalizedExchange)) {
                return new StockKey(stockCode, normalizedExchange);
            }
            return null;
        }

        int dot = trimmed.lastIndexOf('.');
        if (dot > 0 && dot < trimmed.length() - 1) {
            String stockCode = trimmed.substring(0, dot).trim();
            String exchange = trimmed.substring(dot + 1).trim();
            String normalizedExchange = normalizeExchangeCode(exchange);
            if (!stockCode.isBlank() && isSupportedExchange(normalizedExchange)) {
                return new StockKey(stockCode, normalizedExchange);
            }
        }

        return null;
    }

    private String normalizeStockCode(String rawStockCode) {
        if (rawStockCode == null) return null;
        String trimmed = rawStockCode.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String resolveExchangeCode(String exchangeCode) {
        if (exchangeCode == null) return null;
        String trimmed = exchangeCode.trim();
        if (trimmed.isBlank()) return null;
        return normalizeExchangeCode(trimmed);
    }

    private String normalizeExchangeCode(String msExchange) {
        if (msExchange == null) return null;
        String trimmed = msExchange.trim();
        if (trimmed.isBlank()) return null;

        return switch (trimmed.toUpperCase()) {
            case "XKRX" -> "KRX";
            case "XKOS" -> "KOSDAQ";
            case "XNYS" -> "NYSE";
            case "XNAS" -> "NASDAQ";
            default -> trimmed.toUpperCase();
        };
    }

    private boolean isSupportedExchange(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank()) return false;
        return switch (exchangeCode) {
            case "KRX", "KOSDAQ", "NYSE", "NASDAQ" -> true;
            default -> false;
        };
    }

    private record StockKey(String stockCode, String exchangeCode) {}
}
