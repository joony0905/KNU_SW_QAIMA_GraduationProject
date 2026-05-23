package com.qaima.service.batch;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisMarketDataSyncScheduler {

    private final KisBatchProperties properties;
    private final IndustryIndexOhlcvSyncService industryIndexOhlcvSyncService;
    private final InvestorFlowBatchSyncService investorFlowBatchSyncService;

    private final AtomicBoolean industryIndexRunning = new AtomicBoolean(false);
    private final AtomicBoolean marketInvestorFlowRunning = new AtomicBoolean(false);
    private final AtomicBoolean stockInvestorFlowRunning = new AtomicBoolean(false);

    @Scheduled(cron = "${kis-batch.industry-index-ohlcv.daily-cron:0 10 18 * * MON-FRI}", zone = "Asia/Seoul")
    public void runIndustryIndexOhlcvSync() {
        if (!properties.getIndustryIndexOhlcv().isEnabled()) {
            log.info("[IndustryIndexOhlcvBatch] skipped because kis-batch.industry-index-ohlcv.enabled=false");
            return;
        }
        runGuarded("IndustryIndexOhlcvBatch", industryIndexRunning, industryIndexOhlcvSyncService::syncDaily);
    }

    @Scheduled(cron = "${kis-batch.market-investor-flow.daily-cron:0 30 16 * * MON-FRI}", zone = "Asia/Seoul")
    public void runMarketInvestorFlowSync() {
        if (!properties.getMarketInvestorFlow().isEnabled()) {
            log.info("[MarketInvestorFlowBatch] skipped because kis-batch.market-investor-flow.enabled=false");
            return;
        }
        runGuarded("MarketInvestorFlowBatch", marketInvestorFlowRunning, investorFlowBatchSyncService::syncMarketInvestorFlow);
    }

    @Scheduled(cron = "${kis-batch.stock-investor-flow.daily-cron:0 0 19 * * MON-FRI}", zone = "Asia/Seoul")
    public void runStockInvestorFlowSync() {
        if (!properties.getStockInvestorFlow().isEnabled()) {
            log.info("[StockInvestorFlowBatch] skipped because kis-batch.stock-investor-flow.enabled=false");
            return;
        }
        runGuarded("StockInvestorFlowBatch", stockInvestorFlowRunning, investorFlowBatchSyncService::syncStockInvestorFlow);
    }

    private void runGuarded(String jobName, AtomicBoolean running, java.util.function.Supplier<BatchSummary> job) {
        if (!running.compareAndSet(false, true)) {
            log.info("[{}] skipped because previous run is still active", jobName);
            return;
        }

        try {
            BatchSummary summary = job.get();
            log.info("[{}] scheduled run complete. {}", jobName, summary.toLogString());
        } catch (Exception e) {
            log.error("[{}] scheduled run failed", jobName, e);
        } finally {
            running.set(false);
        }
    }
}
