package com.qaima.service.batch;

import com.qaima.common.ErrorCode;
import com.qaima.common.ErrorException;
import com.qaima.domain.MarketInvestorFlow;
import com.qaima.domain.Stock;
import com.qaima.domain.StockInvestorFlow;
import com.qaima.external.KisInvestorFlowClient;
import com.qaima.repository.MarketInvestorFlowRepository;
import com.qaima.repository.StockInvestorFlowRepository;
import com.qaima.repository.StockRepository;
import com.qaima.service.investorflow.MarketInvestorFlowService;
import com.qaima.service.investorflow.StockInvestorFlowService;
import com.qaima.service.tradingcalendar.TradingCalendarService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestorFlowBatchSyncService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalTime KIS_INVESTOR_FLOW_AVAILABLE_FROM = LocalTime.of(15, 40);
    private static final String KRX_MARKET = "KRX";
    private static final List<String> MARKET_CODES = List.of("KSP", "KSQ");

    private final KisBatchProperties properties;
    private final KisBatchRetrySupport retrySupport;
    private final TradingCalendarService tradingCalendarService;
    private final StockRepository stockRepository;
    private final StockInvestorFlowRepository stockInvestorFlowRepository;
    private final MarketInvestorFlowRepository marketInvestorFlowRepository;
    private final StockInvestorFlowService stockInvestorFlowService;
    private final MarketInvestorFlowService marketInvestorFlowService;

    public BatchSummary syncMarketInvestorFlow() {
        KisBatchProperties.MarketInvestorFlow config = properties.getMarketInvestorFlow();
        LocalDate effectiveTo = effectiveInvestorFlowToDate(ZonedDateTime.now(KST));
        BatchCounter counter = new BatchCounter();
        List<String> failedTargets = new ArrayList<>();

        for (String marketCode : MARKET_CODES) {
            String industryCode = KisInvestorFlowClient.defaultMarketIndustryCode(marketCode);
            try {
                FetchRange range = decideMarketFetchRange(
                        marketCode,
                        industryCode,
                        effectiveTo,
                        config.getLookbackDays(),
                        config.getRefreshTailDays()
                );
                if (range.skip()) {
                    counter.skipped++;
                    continue;
                }

                KisBatchRetrySupport.RetryOutcome<List<MarketInvestorFlow>> outcome = retrySupport.callWithOutcome(
                        "marketInvestorFlow:" + marketCode,
                        config.getRetry(),
                        () -> marketInvestorFlowService.sync(marketCode, industryCode, range.from(), range.to()).block()
                );
                if (outcome.attempts() > 1) {
                    counter.retried++;
                }
                if (outcome.recoveredByRetry()) {
                    counter.retryRecovered++;
                }
                List<MarketInvestorFlow> rows = outcome.value();
                int saved = rows == null ? 0 : rows.size();
                if (saved == 0) {
                    counter.empty++;
                } else {
                    counter.success++;
                    counter.saved += saved;
                }
            } catch (Exception e) {
                countFailedRetry(counter, e);
                if (retrySupport.isTimeLimited(e)) {
                    counter.timeLimited++;
                } else {
                    counter.failed++;
                    failedTargets.add(marketCode);
                }
                log.warn("[MarketInvestorFlowBatch] failed. marketCode={}, cause={}", marketCode, e.getMessage(), e);
            }
        }

        BatchSummary summary = counter.toSummary(MARKET_CODES.size(), failedTargets);
        log.info("[MarketInvestorFlowBatch] finished. {}", summary.toLogString());
        return summary;
    }

    public BatchSummary syncStockInvestorFlow() {
        KisBatchProperties.StockInvestorFlow config = properties.getStockInvestorFlow();
        LocalDate effectiveTo = effectiveInvestorFlowToDate(ZonedDateTime.now(KST));
        List<StockSyncTarget> targets = stockTargets(config.getLimit());
        BatchCounter counter = new BatchCounter();
        List<String> failedTargets = new ArrayList<>();

        for (StockSyncTarget target : targets) {
            Stock stock = target.stock();
            try {
                FetchRange range = decideStockFetchRange(
                        target.latestTradeDate(),
                        effectiveTo,
                        config.getLookbackDays(),
                        config.getRefreshTailDays()
                );
                if (range.skip()) {
                    counter.skipped++;
                    continue;
                }

                KisBatchRetrySupport.RetryOutcome<List<StockInvestorFlow>> outcome = retrySupport.callWithOutcome(
                        "stockInvestorFlow:" + stock.getStockCode(),
                        config.getRetry(),
                        () -> stockInvestorFlowService.backfill(stock.getStockCode(), range.from(), range.to()).block()
                );
                if (outcome.attempts() > 1) {
                    counter.retried++;
                }
                if (outcome.recoveredByRetry()) {
                    counter.retryRecovered++;
                }
                List<StockInvestorFlow> rows = outcome.value();
                int saved = rows == null ? 0 : rows.size();
                if (saved == 0) {
                    counter.empty++;
                } else {
                    counter.success++;
                    counter.saved += saved;
                }
            } catch (Exception e) {
                countFailedRetry(counter, e);
                if (retrySupport.isTimeLimited(e)) {
                    counter.timeLimited++;
                } else {
                    counter.failed++;
                    failedTargets.add(stock.getStockCode());
                }
                log.warn("[StockInvestorFlowBatch] failed. stockCode={}, cause={}",
                        stock.getStockCode(), e.getMessage(), e);
            }

            sleep(config.getSleepMs());
        }

        BatchSummary summary = counter.toSummary(targets.size(), failedTargets);
        log.info("[StockInvestorFlowBatch] finished. {}", summary.toLogString());
        return summary;
    }

    private LocalDate effectiveInvestorFlowToDate(ZonedDateTime now) {
        ZonedDateTime kstNow = now == null ? ZonedDateTime.now(KST) : now.withZoneSameInstant(KST);
        LocalDate today = kstNow.toLocalDate();
        if (!tradingCalendarService.isTradingDay(today, KRX_MARKET)
                || kstNow.toLocalTime().isBefore(KIS_INVESTOR_FLOW_AVAILABLE_FROM)) {
            return tradingCalendarService.previousTradingDay(today, KRX_MARKET);
        }
        return today;
    }

    private FetchRange decideMarketFetchRange(
            String marketCode,
            String industryCode,
            LocalDate effectiveTo,
            int lookbackDays,
            int refreshTailDays
    ) {
        LocalDate latest = marketInvestorFlowRepository
                .findTopByMarketCodeAndIndustryCodeAndTradeDateLessThanEqualOrderByTradeDateDesc(
                        marketCode,
                        industryCode,
                        effectiveTo
                )
                .map(MarketInvestorFlow::getTradeDate)
                .orElse(null);
        return decideRange(latest, effectiveTo, lookbackDays, refreshTailDays);
    }

    private FetchRange decideStockFetchRange(
            LocalDate latest,
            LocalDate effectiveTo,
            int lookbackDays,
            int refreshTailDays
    ) {
        return decideRange(latest, effectiveTo, lookbackDays, refreshTailDays);
    }

    private FetchRange decideRange(LocalDate latest, LocalDate effectiveTo, int lookbackDays, int refreshTailDays) {
        if (latest == null) {
            return new FetchRange(effectiveTo.minusDays(Math.max(1, lookbackDays)), effectiveTo, false);
        }
        if (!latest.isBefore(effectiveTo)) {
            return new FetchRange(latest, effectiveTo, true);
        }
        LocalDate from = latest.minusDays(2);
        LocalDate tail = effectiveTo.minusDays(Math.max(1, refreshTailDays));
        if (tail.isAfter(from)) {
            from = tail;
        }
        return new FetchRange(from, effectiveTo, false);
    }

    private List<StockSyncTarget> stockTargets(int limit) {
        return stockRepository.findAllByOrderByStockCodeAsc().stream()
                .filter(this::isDomesticEquityTarget)
                .map(stock -> new StockSyncTarget(stock, latestStockInvestorFlowDate(stock)))
                .sorted(Comparator
                        .comparing((StockSyncTarget target) -> target.latestTradeDate() == null ? LocalDate.MIN : target.latestTradeDate())
                        .thenComparing(target -> target.stock().getStockCode()))
                .limit(limit > 0 ? limit : Long.MAX_VALUE)
                .toList();
    }

    private boolean isDomesticEquityTarget(Stock stock) {
        if (stock == null || stock.getStockId() == null || stock.getDelistedAt() != null) {
            return false;
        }
        if (stock.getAssetType() != null && !"EQUITY".equalsIgnoreCase(stock.getAssetType())) {
            return false;
        }
        String exchangeCode = stock.getExchange() == null ? "" : stock.getExchange().getCode();
        return "KOSPI".equalsIgnoreCase(exchangeCode)
                || "KOSDAQ".equalsIgnoreCase(exchangeCode)
                || "KONEX".equalsIgnoreCase(exchangeCode);
    }

    private LocalDate latestStockInvestorFlowDate(Stock stock) {
        return stockInvestorFlowRepository
                .findTopByStockAndTradeDateLessThanEqualOrderByTradeDateDesc(stock, LocalDate.MAX)
                .map(StockInvestorFlow::getTradeDate)
                .orElse(null);
    }

    private void sleep(long sleepMs) {
        if (sleepMs <= 0) {
            return;
        }
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorException(ErrorCode.INTERNAL_ERROR, "Interrupted during stock investor flow batch sleep");
        }
    }

    private void countFailedRetry(BatchCounter counter, Exception e) {
        if (e instanceof KisBatchRetrySupport.RetryFailedException retryFailedException
                && retryFailedException.attempts() > 1) {
            counter.retried++;
        }
    }

    private record FetchRange(LocalDate from, LocalDate to, boolean skip) {
    }

    private record StockSyncTarget(Stock stock, LocalDate latestTradeDate) {
    }

    private static class BatchCounter {
        int success;
        int skipped;
        int empty;
        int timeLimited;
        int failed;
        int retried;
        int retryRecovered;
        int saved;

        BatchSummary toSummary(int target, List<String> failedTargets) {
            return new BatchSummary(target, success, skipped, empty, timeLimited, failed, retried, retryRecovered, saved, failedTargets);
        }
    }
}
