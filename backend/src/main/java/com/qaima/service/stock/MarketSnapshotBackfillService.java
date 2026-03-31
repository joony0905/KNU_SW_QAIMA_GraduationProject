package com.qaima.service.stock;

import com.qaima.common.Blocking;
import com.qaima.domain.Exchange;
import com.qaima.domain.MarketSnapshot;
import com.qaima.domain.Stock;
import com.qaima.dto.stock.MarketSnapshotBackfillResult;
import com.qaima.dto.stock.MarketSnapshotDto;
import com.qaima.external.KrStockClient;
import com.qaima.repository.MarketSnapshotRepository;
import com.qaima.repository.StockRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class MarketSnapshotBackfillService {

    private static final String DEFAULT_EXCHANGE = "KOSPI";
    private static final long DEFAULT_DELAY_MS = 100L;
    private static final int DEFAULT_LIMIT = 50;
    private static final String SOURCE_OPENDART_PRIMARY = "OPENDART_PRIMARY";

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
        if (stockCode == null || stockCode.isBlank()) {
            return Mono.error(new IllegalArgumentException("stockCode is required"));
        }

        LocalDate targetDate = resolveAsOfDate(asOfDate);
        String resolvedExchange = resolveExchangeCode(exchangeCode);
        String trimmedStockCode = stockCode.trim();

        return Blocking.call(() -> stockRepository.findByExchangeCodeAndStockCodeIgnoreCase(resolvedExchange, trimmedStockCode)
                        .orElseGet(() -> stockRepository.findByStockCodeWithExchange(trimmedStockCode)
                                .orElseThrow(() -> new IllegalArgumentException("Unknown stockCode: " + trimmedStockCode))))
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
        int max = (limit == null || limit <= 0) ? DEFAULT_LIMIT : limit;
        long delay = (delayMs == null || delayMs < 0) ? DEFAULT_DELAY_MS : delayMs;

        return Blocking.call(() -> loadStocks(resolvedExchange))
                .flatMapMany(Flux::fromIterable)
                .concatMap(stock -> shouldBackfill(stock, targetDate)
                        .flatMap(should -> should
                                ? backfillStock(stock, targetDate, true)
                                : Mono.empty()))
                .delayElements(Duration.ofMillis(delay))
                .take(max)
                .collectList();
    }

    private List<Stock> loadStocks(String exchangeCode) {
        if (exchangeCode == null || exchangeCode.isBlank() || "ALL".equalsIgnoreCase(exchangeCode)) {
            return stockRepository.findAllByOrderByStockCodeAsc();
        }
        return stockRepository.findByExchange_CodeIgnoreCaseOrderByStockCodeAsc(exchangeCode);
    }

    private Mono<Boolean> shouldBackfill(Stock stock, LocalDate targetDate) {
        if (!isKisEligible(stock)) {
            return Mono.just(false);
        }

        return Blocking.call(() -> marketSnapshotRepository.findByStockAndAsOfDate(stock, targetDate))
                .map(opt -> isSnapshotIncomplete(opt.orElse(null)));
    }

    private Mono<MarketSnapshotBackfillResult> backfillStock(Stock stock, LocalDate targetDate, boolean force) {
        if (!isKisEligible(stock)) {
            return Mono.just(resultSkipped(stock, targetDate, "NOT_ELIGIBLE"));
        }

        return Blocking.call(() -> marketSnapshotRepository.findByStockAndAsOfDate(stock, targetDate))
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
                            .onErrorResume(ex -> Mono.just(resultFailed(stock, targetDate, ex.getMessage())));
                });
    }

    private LocalDate resolveAsOfDate(LocalDate asOfDate) {
        LocalDate targetDate = (asOfDate == null) ? LocalDate.now() : asOfDate;
        if (!LocalDate.now().equals(targetDate)) {
            throw new IllegalArgumentException("asOfDate must be today for KIS backfill");
        }
        return targetDate;
    }

    private boolean isSnapshotIncomplete(MarketSnapshot snapshot) {
        if (snapshot == null) {
            return true;
        }

        if (snapshot.getMarketCap() == null
                || snapshot.getPer() == null
                || snapshot.getPbr() == null
                || snapshot.getSharesOutstanding() == null
                || snapshot.getSource() == null
                || snapshot.getSource().isBlank()) {
            return true;
        }

        if (!requiresOwnershipMetrics(snapshot)) {
            return false;
        }

        return snapshot.getFloatMarketCap() == null
                || snapshot.getFloatRatio() == null
                || snapshot.getTreasuryRatio() == null;
    }

    private boolean requiresOwnershipMetrics(MarketSnapshot snapshot) {
        return SOURCE_OPENDART_PRIMARY.equalsIgnoreCase(snapshot.getSource());
    }

    private boolean isKisEligible(Stock stock) {
        if (stock == null || stock.getExchange() == null) {
            return false;
        }

        String code = stock.getExchange().getCode();
        if (code == null || code.isBlank()) {
            return false;
        }

        return switch (code.trim().toUpperCase()) {
            case "KRX", "KOSPI", "KOSDAQ", "KONEX" -> true;
            default -> false;
        };
    }

    private String toKisMarketDivCode(Exchange exchange) {
        if (exchange == null || exchange.getCode() == null) {
            return "B";
        }

        return switch (exchange.getCode().trim().toUpperCase()) {
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

        return switch (exchangeCode.trim().toUpperCase()) {
            case "XKRX", "KRX" -> "KOSPI";
            case "XKOS" -> "KOSDAQ";
            default -> exchangeCode.trim().toUpperCase();
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
