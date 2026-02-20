package com.qaima.service;

import com.qaima.common.Blocking;
import com.qaima.domain.Exchange;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import com.qaima.dto.MarketSnapshotBackfillResult;
import com.qaima.dto.MarketSnapshotDto;
import com.qaima.external.KrStockClient;
import com.qaima.repository.MarketSnapshotRepository;
import com.qaima.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketSnapshotBackfillService {

    private static final String DEFAULT_EXCHANGE = "KOSPI";
    private static final long DEFAULT_DELAY_MS = 100L;

    private final StockService stockService;
    private final StockRepository stockRepository;
    private final MarketSnapshotRepository marketSnapshotRepository;
    private final MarketSnapshotService marketSnapshotService;
    private final KrStockClient krStockClient;

    public Mono<MarketSnapshotBackfillResult> backfillOne(
            String stockCode,
            String exchangeCode,
            LocalDate asOfDate,
            boolean force
    ) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        return stockService.getStockByCode(stockCode, exchangeCode)
                .flatMap(stock -> backfillStock(stock, targetDate, force));
    }

    public Mono<List<MarketSnapshotBackfillResult>> backfillMissing(
            String exchangeCode,
            Integer limit,
            Long delayMs,
            LocalDate asOfDate
    ) {
        LocalDate targetDate = resolveAsOfDate(asOfDate);
        String resolvedExchange = resolveExchangeCode(exchangeCode);
        int max = (limit == null || limit <= 0) ? 50 : limit;
        long delay = (delayMs == null || delayMs < 0) ? DEFAULT_DELAY_MS : delayMs;

        return Blocking.call(() -> loadStocks(resolvedExchange))
                .flatMapMany(stocks -> Flux.fromIterable(stocks))
                .concatMap(stock -> shouldBackfill(stock, targetDate)
                        .flatMap(should -> should
                                ? backfillStock(stock, targetDate, true)
                                : Mono.empty()
                        ))
                .delayElements(Duration.ofMillis(delay))
                .take(max)
                .collectList();
    }

    private List<Stock> loadStocks(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank() || "ALL".equalsIgnoreCase(exchangeCode)) {
            return stockRepository.findAll();
        }
        return stockRepository.findByExchange_CodeIgnoreCaseOrderByStockCodeAsc(exchangeCode);
    }

    private Mono<Boolean> shouldBackfill(Stock stock, LocalDate targetDate) {
        if (!isKisEligible(stock)) {
            return Mono.just(false);
        }
        return Blocking.call(() -> marketSnapshotRepository
                .findByStockAndAsOfDate(stock, targetDate))
                .map(opt -> isSnapshotIncomplete(opt.orElse(null)));
    }

    private Mono<MarketSnapshotBackfillResult> backfillStock(
            Stock stock,
            LocalDate targetDate,
            boolean force
    ) {
        if (!isKisEligible(stock)) {
            return Mono.just(resultSkipped(stock, targetDate, "NOT_ELIGIBLE"));
        }

        return Blocking.call(() -> marketSnapshotRepository
                .findByStockAndAsOfDate(stock, targetDate))
                .flatMap(opt -> {
                    MarketSnapshot existing = opt.orElse(null);
                    if (!force && !isSnapshotIncomplete(existing)) {
                        return Mono.just(resultSkipped(
                                stock,
                                targetDate,
                                "ALREADY_FILLED",
                                existing != null ? marketSnapshotService.toDto(existing) : null
                        ));
                    }

                    String marketDivCode = toKisMarketDivCode(stock.getExchange());
                    if ("B".equals(marketDivCode)) {
                        return Mono.just(resultSkipped(stock, targetDate, "NOT_ELIGIBLE"));
                    }

                    return krStockClient.fetchKisStatRaw(stock.getStockCode(), marketDivCode)
                            .flatMap(output -> marketSnapshotService.upsertFromKis(stock, output, targetDate))
                            .map(saved -> resultUpdated(stock, targetDate, marketSnapshotService.toDto(saved)))
                            .onErrorResume(e -> Mono.just(resultFailed(stock, targetDate, e.getMessage())));
                });
    }

    private LocalDate resolveAsOfDate(LocalDate asOfDate) {
        LocalDate target = (asOfDate == null) ? LocalDate.now() : asOfDate;
        if (!LocalDate.now().equals(target)) {
            throw new IllegalArgumentException("asOfDate must be today for KIS backfill");
        }
        return target;
    }

    private boolean isSnapshotIncomplete(MarketSnapshot snapshot) {
        if (snapshot == null) return true;
        return snapshot.getMarketCap() == null
                || snapshot.getPer() == null
                || snapshot.getPbr() == null;
    }

    private boolean isKisEligible(Stock stock) {
        if (stock == null || stock.getExchange() == null) return false;
        String code = stock.getExchange().getCode();
        if (code == null || code.isBlank()) return false;
        return switch (code.trim().toUpperCase()) {
            case "KRX", "KOSPI", "KOSDAQ", "KONEX" -> true;
            default -> false;
        };
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null || exchange.getCode() == null) return "B";
        return switch (exchange.getCode().toUpperCase()) {
            case "KRX", "XKRX", "KOSPI" -> "J";
            case "KOSDAQ", "XKOS" -> "Q";
            case "KONEX" -> "K";
            default -> "B";
        };
    }

    private String resolveExchangeCode(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank()) {
            return DEFAULT_EXCHANGE;
        }
        String trimmed = exchangeCode.trim();
        return switch (trimmed.toUpperCase()) {
            case "XKRX", "KRX" -> "KOSPI";
            case "XKOS" -> "KOSDAQ";
            default -> trimmed.toUpperCase();
        };
    }

    private MarketSnapshotBackfillResult resultUpdated(Stock stock, LocalDate targetDate, MarketSnapshotDto dto) {
        return MarketSnapshotBackfillResult.builder()
                .stockCode(stock.getStockCode())
                .exchangeCode(stock.getExchange() != null ? stock.getExchange().getCode() : null)
                .asOfDate(targetDate)
                .status("UPDATED")
                .message("KIS snapshot upserted")
                .snapshot(dto)
                .build();
    }

    private MarketSnapshotBackfillResult resultSkipped(Stock stock, LocalDate targetDate, String reason) {
        return resultSkipped(stock, targetDate, reason, null);
    }

    private MarketSnapshotBackfillResult resultSkipped(
            Stock stock,
            LocalDate targetDate,
            String reason,
            MarketSnapshotDto dto
    ) {
        return MarketSnapshotBackfillResult.builder()
                .stockCode(stock.getStockCode())
                .exchangeCode(stock.getExchange() != null ? stock.getExchange().getCode() : null)
                .asOfDate(targetDate)
                .status("SKIPPED")
                .message(reason)
                .snapshot(dto)
                .build();
    }

    private MarketSnapshotBackfillResult resultFailed(Stock stock, LocalDate targetDate, String message) {
        return MarketSnapshotBackfillResult.builder()
                .stockCode(stock.getStockCode())
                .exchangeCode(stock.getExchange() != null ? stock.getExchange().getCode() : null)
                .asOfDate(targetDate)
                .status("FAILED")
                .message(message == null ? "KIS backfill failed" : message)
                .snapshot(null)
                .build();
    }
}
