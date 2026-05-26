package com.qaima.service.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisReportCleanupScheduler {

    private final AnalysisReportService analysisReportService;

    @Value("${analysis-report.cleanup.enabled:true}")
    private boolean cleanupEnabled;

    @Scheduled(cron = "${analysis-report.cleanup.daily-cron:0 20 4 * * *}", zone = "Asia/Seoul")
    public void cleanupExpiredReports() {
        if (!cleanupEnabled) {
            log.info("[AnalysisReportCleanup] skipped because analysis-report.cleanup.enabled=false");
            return;
        }

        try {
            Long deleted = analysisReportService.deleteExpiredReports().block();
            log.info("[AnalysisReportCleanup] expired reports deleted. count={}", deleted != null ? deleted : 0);
        } catch (Exception e) {
            log.error("[AnalysisReportCleanup] expired report cleanup failed", e);
        }
    }
}
